package com.derashanwari.user.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.derashanwari.user.R;
import com.derashanwari.user.firebase.FirestoreSyncManager;
import com.derashanwari.user.model.ChatMessage;
import com.derashanwari.user.model.Customer;
import com.derashanwari.user.model.Order;
import com.derashanwari.user.model.OrderEvent;
import com.derashanwari.user.model.OrderItem;
import com.derashanwari.user.model.OrderStatus;
import com.derashanwari.user.model.RestaurantSettings;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.util.CurrencyUtil;
import com.derashanwari.user.util.TimeFormatUtil;
import com.derashanwari.user.util.ValidationUtil;
import com.derashanwari.user.util.WhatsAppHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class OrderDetailActivity extends AppCompatActivity implements FirestoreSyncManager.DataChangeListener {
    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private String orderId;
    private Order order;
    private TextView tvChatLog, tvLiveTimer;
    private EditText etChatMessage;
    private MaterialButton btnCancelOrder;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (order != null) {
                updateLiveTimerLabel();
                if (!OrderStatus.isCompletedOrCancelled(order.getOrderStatus())) {
                    timerHandler.postDelayed(this, 1000L);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        order = RepositoryProvider.orders(this).getOrderById(orderId);
        if (order == null) {
            Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvLiveTimer = findViewById(R.id.tv_order_live_timer);
        btnCancelOrder = findViewById(R.id.btn_cancel_order);
        btnCancelOrder.setOnClickListener(v -> {
            if (OrderStatus.PENDING.equalsIgnoreCase(order.getOrderStatus())) {
                Customer c = RepositoryProvider.auth(this).getCurrentCustomer();
                String uid = c != null && ValidationUtil.isNonEmpty(c.getId()) ? c.getId() : order.getCustomerId();
                String uname = c != null && ValidationUtil.isNonEmpty(c.getName()) ? c.getName() : order.getCustomerName();
                RepositoryProvider.orders(this).advanceOrderStage(
                        order.getOrderId(),
                        OrderStatus.CANCELLED,
                        OrderEvent.CANCELLED,
                        OrderEvent.TS_CANCELLED,
                        uid,
                        "CUSTOMER",
                        uname,
                        "Cancelled by Customer"
                );
                Toast.makeText(this, "Order cancelled", Toast.LENGTH_SHORT).show();
                refreshScreen();
            }
        });

        findViewById(R.id.btn_send_whatsapp).setOnClickListener(v -> {
            RestaurantSettings settings = RepositoryProvider.settings(this).getRestaurantSettings();
            String msg = WhatsAppHelper.buildOrderMessage(order, settings);
            String waNumber = settings != null ? settings.getWhatsappNumber() : "923001112233";
            WhatsAppHelper.openWhatsAppWithMessage(this, waNumber, msg);
        });

        findViewById(R.id.btn_digital_receipt).setOnClickListener(v -> {
            Intent i = new Intent(this, DigitalReceiptActivity.class);
            i.putExtra(DigitalReceiptActivity.EXTRA_ORDER_ID, order.getOrderId());
            startActivity(i);
        });

        tvChatLog = findViewById(R.id.tv_chat_log);
        etChatMessage = findViewById(R.id.et_chat_message);
        findViewById(R.id.btn_send_chat).setOnClickListener(v -> sendChat());

        refreshScreen();
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
    protected void onResume() {
        super.onResume();
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onDataChanged() {
        refreshScreen();
    }

    private void refreshScreen() {
        Order fresh = RepositoryProvider.orders(this).getOrderById(orderId);
        if (fresh != null) order = fresh;
        if (order == null) return;
        bindOrderDetails();
        refreshChat();
    }

    private void updateLiveTimerLabel() {
        if (tvLiveTimer == null || order == null) return;
        long placed = order.getPlacedTimestamp();
        if (OrderStatus.isCompletedOrCancelled(order.getOrderStatus())) {
            long end = order.getTimestampForKey(OrderEvent.TS_DELIVERED);
            if (end <= 0L) end = order.getTimestampForKey(OrderEvent.TS_CANCELLED);
            if (end <= 0L) end = order.getUpdatedAt();
            tvLiveTimer.setText("Total: " + TimeFormatUtil.formatDuration(Math.max(0L, end - placed)));
            tvLiveTimer.setTextColor(ContextCompat.getColor(this, R.color.status_success));
        } else {
            tvLiveTimer.setText("⏱ " + TimeFormatUtil.formatElapsedTimer(placed, System.currentTimeMillis()));
            tvLiveTimer.setTextColor(ContextCompat.getColor(this, R.color.brand_orange_dark));
        }
    }

    private void bindOrderDetails() {
        ((TextView) findViewById(R.id.tv_order_id)).setText("Order " + order.getOrderId());
        ((TextView) findViewById(R.id.tv_order_status_big)).setText(order.getOrderStatus());
        updateLiveTimerLabel();

        String riderText = ValidationUtil.isNonEmpty(order.getRiderName())
                ? " • Rider: " + order.getRiderName() : "";
        ((TextView) findViewById(R.id.tv_order_meta)).setText(
                "Placed (PKT): " + TimeFormatUtil.formatDateTimePkt(order.getPlacedTimestamp())
                        + "\n" + order.getPaymentMethod() + " (" + order.getPaymentStatus() + ")" + riderText
                        + "\nDeliver to: " + order.getDeliveryAddress());

        btnCancelOrder.setVisibility(OrderStatus.PENDING.equalsIgnoreCase(order.getOrderStatus())
                ? View.VISIBLE : View.GONE);

        renderTimeline();

        StringBuilder itemsText = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            itemsText.append(item.getQuantity()).append("x ")
                    .append(item.getDisplayName()).append(" — ")
                    .append(CurrencyUtil.formatPkr(item.getLineTotal())).append("\n");
        }
        if (ValidationUtil.isNonEmpty(order.getNotes())) {
            itemsText.append("\nNotes: ").append(order.getNotes());
        }
        ((TextView) findViewById(R.id.tv_order_items_summary)).setText(itemsText.toString().trim());
        ((TextView) findViewById(R.id.tv_order_totals)).setText(
                "Subtotal: " + CurrencyUtil.formatPkr(order.getSubtotal())
                        + "  |  Delivery: " + CurrencyUtil.formatPkr(order.getDeliveryFee())
                        + (order.getDiscountAmount() > 0 ? "  |  Discount: -" + CurrencyUtil.formatPkr(order.getDiscountAmount()) : "")
                        + "\nGrand Total: " + CurrencyUtil.formatPkr(order.getTotalAmount()));
    }

    private void renderTimeline() {
        LinearLayout stepsLayout = findViewById(R.id.layout_tracking_steps);
        stepsLayout.removeAllViews();

        List<OrderEvent> persisted = RepositoryProvider.orders(this).getTimelineForOrder(order.getOrderId());
        List<String> stages = new ArrayList<>(OrderEvent.orderedStages());
        if (OrderStatus.CANCELLED.equalsIgnoreCase(order.getOrderStatus())
                || order.hasTimestamp(OrderEvent.TS_CANCELLED)) {
            stages.add(OrderEvent.CANCELLED);
        }

        int textPrimary = ContextCompat.getColor(this, R.color.text_primary);
        int textSecondary = ContextCompat.getColor(this, R.color.text_secondary);
        int success = ContextCompat.getColor(this, R.color.status_success);
        int danger = ContextCompat.getColor(this, R.color.status_danger);

        long prevTs = 0L;
        for (String stage : stages) {
            OrderEvent matched = null;
            for (OrderEvent ev : persisted) {
                if (stage.equals(ev.getEventType())) {
                    matched = ev;
                    break;
                }
            }
            long ts = matched != null && matched.getTimestamp() > 0
                    ? matched.getTimestamp()
                    : order.getTimestampForKey(OrderEvent.timestampKeyForEvent(stage));
            if (ts <= 0L && OrderEvent.ORDER_PLACED.equals(stage)) {
                ts = order.getPlacedTimestamp();
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 10, 16, 10);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            row.setLayoutParams(lp);
            row.setBackground(ContextCompat.getDrawable(this,
                    ts > 0 ? R.drawable.bg_glass_card_orange : R.drawable.bg_glass_card));

            TextView title = new TextView(this);
            String prefix = ts > 0 ? (OrderEvent.CANCELLED.equals(stage) ? "✕ " : "✓ ") : "○ ";
            title.setText(prefix + OrderEvent.displayLabel(stage));
            title.setTextSize(13.5f);
            title.setTextColor(ts > 0 ? (OrderEvent.CANCELLED.equals(stage) ? danger : success) : textSecondary);
            row.addView(title);

            if (ts > 0L) {
                StringBuilder meta = new StringBuilder();
                meta.append(TimeFormatUtil.formatDateTimePkt(ts));
                if (prevTs > 0L && ts >= prevTs) {
                    meta.append("  (+").append(TimeFormatUtil.formatDuration(ts - prevTs)).append(")");
                }
                if (matched != null && ValidationUtil.isNonEmpty(matched.getUserName())) {
                    meta.append("\nUpdated by ").append(matched.getUserRole()).append(": ").append(matched.getUserName());
                }
                TextView sub = new TextView(this);
                sub.setText(meta.toString());
                sub.setTextSize(11.5f);
                sub.setTextColor(textPrimary);
                row.addView(sub);
                prevTs = ts;
            }
            stepsLayout.addView(row);
        }
    }

    private void refreshChat() {
        List<ChatMessage> msgs = RepositoryProvider.chat(this).getMessagesForOrder(order.getOrderId());
        if (msgs.isEmpty()) {
            tvChatLog.setText("No messages yet. Ask kitchen or rider anything about your order.");
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
        Customer c = RepositoryProvider.auth(this).getCurrentCustomer();
        ChatMessage msg = new ChatMessage(
                "",
                order.getOrderId(),
                c != null ? c.getId() : order.getCustomerId(),
                "CUSTOMER",
                c != null ? c.getName() : order.getCustomerName(),
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
