package com.derashanwari.rider.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.derashanwari.rider.R;
import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.model.ChatMessage;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderItem;
import com.derashanwari.rider.model.OrderStatus;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.util.CurrencyUtil;
import com.derashanwari.rider.util.ValidationUtil;
import com.derashanwari.rider.util.WhatsAppHelper;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class RiderDeliveryDetailActivity extends AppCompatActivity implements FirestoreSyncManager.DataChangeListener {
    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private String orderId;
    private Order order;
    private TextView tvChatLog;
    private EditText etChatMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider_delivery_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        order = RepositoryProvider.orders(this).getOrderById(orderId);
        if (order == null) {
            Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvChatLog = findViewById(R.id.tv_rider_chat_log);
        etChatMessage = findViewById(R.id.et_rider_chat_message);

        bindOrder();
        refreshChat();

        findViewById(R.id.btn_call_customer).setOnClickListener(v -> {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + order.getCustomerPhone()));
            startActivity(dial);
        });

        findViewById(R.id.btn_whatsapp_customer).setOnClickListener(v -> {
            String msg = "Assalam-o-Alaikum " + order.getCustomerName()
                    + ", I am your Dera Shanwari rider for order " + order.getOrderId() + ".";
            WhatsAppHelper.openWhatsAppWithMessage(this, order.getCustomerPhone(), msg);
        });

        findViewById(R.id.btn_status_picked_up).setOnClickListener(v -> updateStatus(OrderStatus.PICKED_UP));
        findViewById(R.id.btn_status_out).setOnClickListener(v -> updateStatus(OrderStatus.OUT_FOR_DELIVERY));
        findViewById(R.id.btn_status_delivered).setOnClickListener(v -> updateStatus(OrderStatus.DELIVERED));
        findViewById(R.id.btn_rider_send_chat).setOnClickListener(v -> sendChat());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).addDataChangeListener(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).removeDataChangeListener(this);
        }
    }

    @Override
    public void onDataChanged() {
        Order fresh = RepositoryProvider.orders(this).getOrderById(orderId);
        if (fresh != null) {
            order = fresh;
            bindOrder();
        }
        refreshChat();
    }

    private void updateStatus(String status) {
        RepositoryProvider.orders(this).updateOrderStatus(order.getOrderId(), status);
        order = RepositoryProvider.orders(this).getOrderById(order.getOrderId());
        bindOrder();
        Toast.makeText(this, "Status updated & synced: " + status, Toast.LENGTH_SHORT).show();
    }

    private void bindOrder() {
        ((TextView) findViewById(R.id.tv_delivery_header)).setText(
                order.getOrderId() + " • " + order.getOrderStatus());
        ((TextView) findViewById(R.id.tv_delivery_customer)).setText(
                "Customer: " + order.getCustomerName()
                        + "\nPhone: " + order.getCustomerPhone()
                        + "\nAddress: " + order.getDeliveryAddress()
                        + "\nPayment: " + order.getPaymentMethod() + " (" + order.getPaymentStatus() + ")"
                        + (ValidationUtil.isNonEmpty(order.getNotes()) ? "\nInstructions: " + order.getNotes() : ""));

        StringBuilder sb = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            sb.append(item.getQuantity()).append("x ").append(item.getProductName()).append("\n");
        }
        sb.append("\nTotal Amount: ").append(CurrencyUtil.formatPkr(order.getTotalAmount()));
        ((TextView) findViewById(R.id.tv_delivery_items)).setText(sb.toString());
    }

    private void refreshChat() {
        List<ChatMessage> msgs = RepositoryProvider.chat(this).getMessagesForOrder(order.getOrderId());
        if (msgs.isEmpty()) {
            tvChatLog.setText("No messages yet. Message the customer or admin if needed.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : msgs) {
            sb.append("[").append(m.getSenderRole()).append("] ")
                    .append(m.getSenderName()).append(": ")
                    .append(m.getMessage()).append("\n");
        }
        tvChatLog.setText(sb.toString().trim());
    }

    private void sendChat() {
        String text = etChatMessage.getText().toString().trim();
        if (!ValidationUtil.isNonEmpty(text)) return;
        Rider rider = RepositoryProvider.auth(this).getCurrentRider();
        ChatMessage msg = new ChatMessage(
                "",
                order.getOrderId(),
                rider != null ? rider.getId() : order.getRiderId(),
                "RIDER",
                rider != null ? rider.getName() : (order.getRiderName() != null ? order.getRiderName() : "Rider"),
                text,
                System.currentTimeMillis()
        );
        msg.setCustomerId(order.getCustomerId());
        msg.setRiderId(order.getRiderId());
        RepositoryProvider.chat(this).sendMessage(msg);
        etChatMessage.setText("");
        refreshChat();
    }
}
