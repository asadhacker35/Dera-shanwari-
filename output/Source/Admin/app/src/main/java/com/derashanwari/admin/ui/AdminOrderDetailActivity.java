package com.derashanwari.admin.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.derashanwari.admin.R;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.AdminUser;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderEvent;
import com.derashanwari.admin.model.OrderItem;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.model.RestaurantSettings;
import com.derashanwari.admin.model.Rider;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.util.CurrencyUtil;
import com.derashanwari.admin.util.EscPosReceiptFormatter;
import com.derashanwari.admin.util.TimeFormatUtil;
import com.derashanwari.admin.util.UsbThermalPrinterManager;
import com.derashanwari.admin.util.ValidationUtil;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class AdminOrderDetailActivity extends AppCompatActivity implements FirestoreSyncManager.DataChangeListener {
    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private String orderId;
    private Order order;
    private Spinner spinnerStatus, spinnerRider;
    private List<Rider> riders;
    private TextView tvLiveTimer, tvPrintAudit;
    private LinearLayout timelineContainer;

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
        setContentView(R.layout.activity_admin_order_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        order = RepositoryProvider.orders(this).getOrderById(orderId);
        if (order == null) {
            Toast.makeText(this, "Order not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        spinnerStatus = findViewById(R.id.spinner_order_status);
        spinnerRider = findViewById(R.id.spinner_assign_rider);
        tvLiveTimer = findViewById(R.id.tv_detail_live_timer);
        tvPrintAudit = findViewById(R.id.tv_print_audit);
        timelineContainer = findViewById(R.id.container_order_timeline);

        bindOrder();
        setupSpinners();

        findViewById(R.id.btn_print_thermal).setOnClickListener(v -> handleThermalPrintRequest());
        findViewById(R.id.btn_open_digital_receipt).setOnClickListener(v -> {
            Intent i = new Intent(this, DigitalReceiptActivity.class);
            i.putExtra(DigitalReceiptActivity.EXTRA_ORDER_ID, order.getOrderId());
            startActivity(i);
        });

        findViewById(R.id.btn_edit_order).setOnClickListener(v -> showEditOrderDialog());
        findViewById(R.id.btn_delete_order).setOnClickListener(v -> showDeleteConfirmDialog());

        findViewById(R.id.btn_update_status).setOnClickListener(v -> {
            String newStatus = (String) spinnerStatus.getSelectedItem();
            AdminUser admin = RepositoryProvider.auth(this).getCurrentAdmin();
            String adminId = admin != null && admin.getId() != null ? admin.getId() : "admin";
            String adminName = admin != null && ValidationUtil.isNonEmpty(admin.getName()) ? admin.getName() : "Admin";
            RepositoryProvider.orders(this).advanceOrderStage(
                    order.getOrderId(),
                    newStatus,
                    OrderStatus.eventTypeForStatus(newStatus),
                    OrderStatus.timestampKeyForStatus(newStatus),
                    adminId,
                    "ADMIN",
                    adminName,
                    "Updated by Admin"
            );
            order = RepositoryProvider.orders(this).getOrderById(order.getOrderId());
            bindOrder();
            Toast.makeText(this, "Order status updated to " + newStatus, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_assign_rider).setOnClickListener(v -> {
            int pos = spinnerRider.getSelectedItemPosition();
            if (pos >= 0 && pos < riders.size()) {
                Rider chosen = riders.get(pos);
                String targetRiderUid = ValidationUtil.isNonEmpty(chosen.getLinkedUid())
                        ? chosen.getLinkedUid() : chosen.getId();
                RepositoryProvider.orders(this).assignRider(order.getOrderId(), targetRiderUid, chosen.getName());
                order = RepositoryProvider.orders(this).getOrderById(order.getOrderId());
                bindOrder();
                setupSpinners();
                if (!RepositoryProvider.isTestMode() && targetRiderUid.startsWith("rider_")) {
                    Toast.makeText(this,
                            "Assigned to " + chosen.getName() + " (Rider must sign into Rider App with registered email to receive live updates)",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Assigned to " + chosen.getName(), Toast.LENGTH_SHORT).show();
                }
            }
        });
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
        Order fresh = RepositoryProvider.orders(this).getOrderById(orderId);
        if (fresh != null) {
            order = fresh;
            bindOrder();
        } else {
            Toast.init = null; // no-op
            finish();
        }
    }

    private void showEditOrderDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_order, null);
        EditText etName = view.findViewById(R.id.et_edit_cust_name);
        EditText etPhone = view.findViewById(R.id.et_edit_cust_phone);
        EditText etAddress = view.findViewById(R.id.et_edit_delivery_address);
        EditText etNotes = view.findViewById(R.id.et_edit_notes);
        EditText etDeliveryFee = view.findViewById(R.id.et_edit_delivery_fee);

        etName.setText(order.getCustomerName());
        etPhone.setText(order.getCustomerPhone());
        etAddress.setText(order.getDeliveryAddress());
        etNotes.setText(order.getNotes());
        etDeliveryFee.setText(String.valueOf(order.getDeliveryFee()));

        new AlertDialog.Builder(this)
                .setTitle("Edit Order Details")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().trim();
                    String address = etAddress.getText().toString().trim();
                    String notes = etNotes.getText().toString().trim();
                    double fee = order.getDeliveryFee();
                    try {
                        fee = Double.parseDouble(etDeliveryFee.getText().toString().trim());
                    } catch (Exception ignored) {}

                    order.setCustomerName(name);
                    order.setCustomerPhone(phone);
                    order.setDeliveryAddress(address);
                    order.setNotes(notes);
                    double subtotal = 0;
                    for (OrderItem item : order.getItems()) {
                        subtotal += item.getLineTotal();
                    }
                    order.setSubtotal(subtotal);
                    order.setDeliveryFee(fee);
                    order.setTotalAmount(subtotal + fee - order.getDiscountAmount());

                    RepositoryProvider.orders(this).updateOrder(order);
                    bindOrder();
                    Toast.makeText(this, "Order updated successfully", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Order")
                .setMessage("Are you sure you want to delete order " + order.getOrderId() + "? This action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    RepositoryProvider.orders(this).deleteOrder(order.getOrderId());
                    Toast.makeText(this, "Order deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleThermalPrintRequest() {
        if (order == null) return;
        if (order.getPrintCount() > 0) {
            String lastTime = order.getLastPrintedAt() > 0
                    ? TimeFormatUtil.formatDateTimePkt(order.getLastPrintedAt())
                    : "earlier";
            new AlertDialog.Builder(this)
                    .setTitle("Duplicate Receipt Warning")
                    .setMessage("Receipt already printed " + order.getPrintCount()
                            + " time(s) (Last: " + lastTime + " by " + order.getLastPrintedBy() + ")."
                            + "\n\nDo you want to print a duplicate copy?")
                    .setPositiveButton("Print Duplicate", (d, w) -> executeThermalPrint())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            executeThermalPrint();
        }
    }

    private void executeThermalPrint() {
        RestaurantSettings settings = RepositoryProvider.settings(this).getRestaurantSettings();
        byte[] payload = EscPosReceiptFormatter.formatOrderReceipt(order, settings);
        Toast.makeText(this, "Sending 80mm receipt to Black Copper BC-87AC...", Toast.LENGTH_SHORT).show();
        UsbThermalPrinterManager.printBytes(this, payload, new UsbThermalPrinterManager.PrintCallback() {
            @Override
            public void onSuccess(String deviceLabel) {
                AdminUser admin = RepositoryProvider.auth(AdminOrderDetailActivity.this).getCurrentAdmin();
                String printedBy = admin != null && ValidationUtil.isNonEmpty(admin.getName())
                        ? admin.getName() : "Admin";
                RepositoryProvider.orders(AdminOrderDetailActivity.this).recordPrint(order.getOrderId(), printedBy);
                order = RepositoryProvider.orders(AdminOrderDetailActivity.this).getOrderById(order.getOrderId());
                bindOrder();
                Toast.makeText(AdminOrderDetailActivity.this, "Printed on " + deviceLabel, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AdminOrderDetailActivity.this, "Printer: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateLiveTimerLabel() {
        if (tvLiveTimer == null || order == null) return;
        long placed = order.getPlacedTimestamp();
        if (OrderStatus.isCompletedOrCancelled(order.getOrderStatus())) {
            long end = order.getTimestampForKey(OrderEvent.TS_DELIVERED);
            if (end <= 0L) end = order.getTimestampForKey(OrderEvent.TS_CANCELLED);
            if (end <= 0L) end = order.getUpdatedAt();
            tvLiveTimer.setText("Total: " + TimeFormatUtil.formatDuration(Math.max(0L, end - placed)));
            tvLiveTimer.setTextColor(ContextCompat.getColor(this, R.color.admin_success));
        } else {
            tvLiveTimer.setText("⏱ " + TimeFormatUtil.formatElapsedTimer(placed, System.currentTimeMillis()));
            tvLiveTimer.setTextColor(ContextCompat.getColor(this, R.color.admin_orange_dark));
        }
    }

    private void bindOrder() {
        ((TextView) findViewById(R.id.tv_order_header)).setText(
                order.getOrderId() + " • " + order.getOrderStatus());
        updateLiveTimerLabel();

        String riderLabel = ValidationUtil.isNonEmpty(order.getRiderName())
                ? order.getRiderName() : "Not Assigned";
        ((TextView) findViewById(R.id.tv_customer_info)).setText(
                "Placed (PKT): " + TimeFormatUtil.formatDateTimePkt(order.getPlacedTimestamp())
                        + "\nCustomer: " + order.getCustomerName()
                        + "\nPhone: " + order.getCustomerPhone()
                        + "\nAddress: " + order.getDeliveryAddress()
                        + "\nPayment: " + order.getPaymentMethod() + " (" + order.getPaymentStatus() + ")"
                        + "\nAssigned Rider: " + riderLabel
                        + (ValidationUtil.isNonEmpty(order.getNotes()) ? "\nNotes: " + order.getNotes() : ""));

        String printerDiag = UsbThermalPrinterManager.describeConnectedPrinter(this);
        if (order.getPrintCount() > 0) {
            tvPrintAudit.setText("Prints: " + order.getPrintCount()
                    + " • Last: " + TimeFormatUtil.formatShortDateTimePkt(order.getLastPrintedAt())
                    + " PKT by " + order.getLastPrintedBy() + "\n" + printerDiag);
        } else {
            tvPrintAudit.setText("Not printed yet • Printable before or after rider assignment\n" + printerDiag);
        }

        StringBuilder sb = new StringBuilder();
        for (OrderItem item : order.getItems()) {
            sb.append(item.getQuantity()).append("x ")
                    .append(item.getDisplayName()).append(" = ")
                    .append(CurrencyUtil.formatPkr(item.getLineTotal())).append("\n");
        }
        sb.append("\nSubtotal: ").append(CurrencyUtil.formatPkr(order.getSubtotal()));
        sb.append("\nDelivery Fee: ").append(CurrencyUtil.formatPkr(order.getDeliveryFee()));
        if (order.getDiscountAmount() > 0) {
            sb.append("\nDiscount: -").append(CurrencyUtil.formatPkr(order.getDiscountAmount()));
        }
        sb.append("\nGrand Total: ").append(CurrencyUtil.formatPkr(order.getTotalAmount()));
        ((TextView) findViewById(R.id.tv_items_breakdown)).setText(sb.toString());

        renderTimeline();
    }

    private void renderTimeline() {
        if (timelineContainer == null || order == null) return;
        timelineContainer.removeAllViews();

        List<OrderEvent> persisted = RepositoryProvider.orders(this).getTimelineForOrder(order.getOrderId());
        List<String> stages = new ArrayList<>(OrderEvent.orderedStages());
        if (OrderStatus.CANCELLED.equalsIgnoreCase(order.getOrderStatus())
                || order.hasTimestamp(OrderEvent.TS_CANCELLED)) {
            stages.add(OrderEvent.CANCELLED);
        }

        int textPrimary = ContextCompat.getColor(this, R.color.admin_text_primary);
        int textMuted = ContextCompat.getColor(this, R.color.admin_text_muted);
        int success = ContextCompat.getColor(this, R.color.admin_success);
        int danger = ContextCompat.getColor(this, R.color.admin_danger);

        long prevTs = 0L;
        for (String stage : stages) {
            OrderEvent matched = null;
            for (OrderEvent ev : persisted) {
                if (stage.equals(ev.getEventType())) {
                    matched = ev;
                    break;
                }
            }
            String tsKey = OrderEvent.timestampKeyForEvent(stage);
            long ts = matched != null && matched.getTimestamp() > 0
                    ? matched.getTimestamp()
                    : order.getTimestampForKey(tsKey);
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
            title.setTextSize(13f);
            title.setTextColor(ts > 0 ? (OrderEvent.CANCELLED.equals(stage) ? danger : success) : textMuted);
            row.addView(title);

            if (ts > 0L) {
                StringBuilder meta = new StringBuilder();
                meta.append(TimeFormatUtil.formatDateTimePkt(ts));
                if (prevTs > 0L && ts >= prevTs) {
                    meta.append("  (+").append(TimeFormatUtil.formatDuration(ts - prevTs)).append(")");
                }
                if (matched != null && ValidationUtil.isNonEmpty(matched.getUserName())) {
                    meta.append("\nBy ").append(matched.getUserRole()).append(": ").append(matched.getUserName());
                }
                TextView sub = new TextView(this);
                sub.setText(meta.toString());
                sub.setTextSize(11.5f);
                sub.setTextColor(textPrimary);
                row.addView(sub);
                prevTs = ts;
            }
            timelineContainer.addView(row);
        }
    }

    private void setupSpinners() {
        List<String> statuses = OrderStatus.allAdminStatuses();
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, statuses);
        spinnerStatus.setAdapter(statusAdapter);
        int idx = statuses.indexOf(order.getOrderStatus());
        if (idx >= 0) spinnerStatus.setSelection(idx);

        riders = RepositoryProvider.riders(this).getActiveRiders();
        List<String> riderNames = new ArrayList<>();
        int selectedRiderIdx = 0;
        for (int i = 0; i < riders.size(); i++) {
            Rider r = riders.get(i);
            boolean linked = ValidationUtil.isNonEmpty(r.getLinkedUid()) && !r.getLinkedUid().startsWith("rider_");
            riderNames.add(r.getName() + " (" + r.getPhone() + ")" + (linked ? "" : " [Unlinked]"));
            if (r.getId().equals(order.getRiderId())
                    || (ValidationUtil.isNonEmpty(r.getLinkedUid()) && r.getLinkedUid().equals(order.getRiderId()))) {
                selectedRiderIdx = i;
            }
        }
        ArrayAdapter<String> riderAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, riderNames);
        spinnerRider.setAdapter(riderAdapter);
        if (!riderNames.isEmpty()) spinnerRider.setSelection(selectedRiderIdx);
    }
}
