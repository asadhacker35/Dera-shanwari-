package com.example.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.firebase.FirestoreSyncManager;
import com.example.model.Order;
import com.example.model.OrderItem;
import com.example.model.OrderStatus;
import com.example.model.RestaurantSettings;
import com.example.repository.RepositoryProvider;
import com.example.util.CurrencyUtil;
import com.example.util.ValidationUtil;
import com.example.util.WhatsAppHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class OrderDetailActivity extends AppCompatActivity implements FirestoreSyncManager.DataChangeListener {
    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private String orderId;
    private Order order;
    private MaterialButton btnCancelOrder;
    private View layoutRatingSection;
    private EditText etReviewComment;
    private final ImageButton[] starButtons = new ImageButton[5];
    private int selectedRating = 5;

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

        btnCancelOrder = findViewById(R.id.btn_cancel_order);
        btnCancelOrder.setOnClickListener(v -> {
            if (OrderStatus.PENDING.equalsIgnoreCase(order.getOrderStatus())) {
                RepositoryProvider.orders(this).updateOrderStatus(order.getOrderId(), OrderStatus.CANCELLED);
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

        layoutRatingSection = findViewById(R.id.layout_rating_section);
        etReviewComment = findViewById(R.id.et_review_comment);
        starButtons[0] = findViewById(R.id.btn_star_1);
        starButtons[1] = findViewById(R.id.btn_star_2);
        starButtons[2] = findViewById(R.id.btn_star_3);
        starButtons[3] = findViewById(R.id.btn_star_4);
        starButtons[4] = findViewById(R.id.btn_star_5);

        for (int i = 0; i < 5; i++) {
            final int rVal = i + 1;
            starButtons[i].setOnClickListener(v -> setRating(rVal));
        }

        findViewById(R.id.btn_submit_review).setOnClickListener(v -> {
            String comment = etReviewComment.getText().toString().trim();
            RepositoryProvider.orders(this).submitRating(order.getOrderId(), selectedRating, comment);
            Toast.makeText(this, "Thank you! Rating & review submitted successfully.", Toast.LENGTH_SHORT).show();
            refreshScreen();
        });

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
    public void onDataChanged() {
        refreshScreen();
    }

    private void refreshScreen() {
        Order fresh = RepositoryProvider.orders(this).getOrderById(orderId);
        if (fresh != null) order = fresh;
        if (order == null) return;
        bindOrderDetails();
    }

    private void bindOrderDetails() {
        ((TextView) findViewById(R.id.tv_order_id)).setText("Order " + order.getOrderId());
        ((TextView) findViewById(R.id.tv_order_status_big)).setText(order.getOrderStatus());
        String riderText = ValidationUtil.isNonEmpty(order.getRiderName())
                ? " • Rider: " + order.getRiderName() : "";
        ((TextView) findViewById(R.id.tv_order_meta)).setText(
                order.getPaymentMethod() + " (" + order.getPaymentStatus() + ")" + riderText
                        + "\nDeliver to: " + order.getDeliveryAddress());

        btnCancelOrder.setVisibility(OrderStatus.PENDING.equalsIgnoreCase(order.getOrderStatus())
                ? View.VISIBLE : View.GONE);

        LinearLayout stepsLayout = findViewById(R.id.layout_tracking_steps);
        stepsLayout.removeAllViews();
        List<String> steps = OrderStatus.customerTrackingSteps();
        int currentIdx = steps.indexOf(order.getOrderStatus());
        if (OrderStatus.PICKED_UP.equalsIgnoreCase(order.getOrderStatus())) {
            currentIdx = steps.indexOf(OrderStatus.ASSIGNED);
        }
        for (int i = 0; i < steps.size(); i++) {
            TextView tv = new TextView(this);
            boolean reached = (currentIdx >= i) && !OrderStatus.CANCELLED.equalsIgnoreCase(order.getOrderStatus());
            tv.setText((reached ? "● " : "○ ") + steps.get(i));
            tv.setTextSize(14f);
            tv.setPadding(8, 8, 8, 8);
            tv.setTextColor(ContextCompat.getColor(this,
                    reached ? R.color.brand_orange_dark : R.color.text_secondary));
            stepsLayout.addView(tv);
        }

        StringBuilder itemsText = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            itemsText.append(item.getQuantity()).append("x ")
                    .append(item.getProductName()).append(" — ")
                    .append(CurrencyUtil.formatPkr(item.getLineTotal())).append("\n");
        }
        if (ValidationUtil.isNonEmpty(order.getNotes())) {
            itemsText.append("\nNotes: ").append(order.getNotes());
        }
        ((TextView) findViewById(R.id.tv_order_items_summary)).setText(itemsText.toString().trim());
        ((TextView) findViewById(R.id.tv_order_totals)).setText(
                "Subtotal: " + CurrencyUtil.formatPkr(order.getSubtotal())
                        + "  |  Delivery: " + CurrencyUtil.formatPkr(order.getDeliveryFee())
                        + "\nGrand Total: " + CurrencyUtil.formatPkr(order.getTotalAmount()));

        boolean isDelivered = OrderStatus.DELIVERED.equalsIgnoreCase(order.getOrderStatus());
        layoutRatingSection.setVisibility(isDelivered ? View.VISIBLE : View.GONE);
        if (order.getRating() > 0) {
            setRating(order.getRating());
            if (ValidationUtil.isNonEmpty(order.getReviewComment())) {
                etReviewComment.setText(order.getReviewComment());
            }
        }
    }

    private void setRating(int rating) {
        selectedRating = rating;
        for (int i = 0; i < 5; i++) {
            if (i < rating) {
                starButtons[i].setColorFilter(ContextCompat.getColor(this, R.color.brand_orange));
            } else {
                starButtons[i].setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }
    }
}
