package com.derashanwari.admin.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.derashanwari.admin.R;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.util.CurrencyUtil;
import com.derashanwari.admin.util.OrderReportCalculator;
import com.derashanwari.admin.util.TimeFormatUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AdminOrderReportActivity extends AppCompatActivity implements FirestoreSyncManager.DataChangeListener {
    private static final int MODE_TODAY = 0;
    private static final int MODE_YESTERDAY = 1;
    private static final int MODE_7D = 2;
    private static final int MODE_30D = 3;
    private static final int MODE_CUSTOM = 4;

    private int activeMode = MODE_TODAY;
    private long customStartMs;
    private long customEndMs;

    private MaterialButton btnToday, btnYesterday, btn7d, btn30d, btnCustom;
    private TextView tvRangeLabel, tvKpis, tvTimings, tvOrderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_order_report);

        MaterialToolbar tb = findViewById(R.id.toolbar_report);
        tb.setNavigationOnClickListener(v -> finish());

        btnToday = findViewById(R.id.btn_rep_today);
        btnYesterday = findViewById(R.id.btn_rep_yesterday);
        btn7d = findViewById(R.id.btn_rep_7d);
        btn30d = findViewById(R.id.btn_rep_30d);
        btnCustom = findViewById(R.id.btn_rep_custom);

        tvRangeLabel = findViewById(R.id.tv_report_range_label);
        tvKpis = findViewById(R.id.tv_report_kpis);
        tvTimings = findViewById(R.id.tv_report_timings);
        tvOrderList = findViewById(R.id.tv_report_order_list);

        customStartMs = TimeFormatUtil.startOfDayPkt(6);
        customEndMs = TimeFormatUtil.endOfDayPkt(0);

        btnToday.setOnClickListener(v -> selectMode(MODE_TODAY));
        btnYesterday.setOnClickListener(v -> selectMode(MODE_YESTERDAY));
        btn7d.setOnClickListener(v -> selectMode(MODE_7D));
        btn30d.setOnClickListener(v -> selectMode(MODE_30D));
        btnCustom.setOnClickListener(v -> pickCustomRange());

        selectMode(MODE_TODAY);
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
        renderReport();
    }

    private void selectMode(int mode) {
        activeMode = mode;
        styleButtons();
        renderReport();
    }

    private void styleButtons() {
        int activeBg = ContextCompat.getColor(this, R.color.admin_orange);
        int idleBg = ContextCompat.getColor(this, R.color.admin_surface_elevated);
        int white = ContextCompat.getColor(this, R.color.admin_white);
        int dark = ContextCompat.getColor(this, R.color.admin_text_primary);

        MaterialButton[] btns = {btnToday, btnYesterday, btn7d, btn30d, btnCustom};
        for (int i = 0; i < btns.length; i++) {
            boolean sel = (i == activeMode);
            btns[i].setBackgroundColor(sel ? activeBg : idleBg);
            btns[i].setTextColor(sel ? white : dark);
        }
    }

    private void pickCustomRange() {
        Calendar pktCal = Calendar.getInstance(TimeFormatUtil.PKT_ZONE, Locale.US);
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar start = Calendar.getInstance(TimeFormatUtil.PKT_ZONE, Locale.US);
            start.set(year, month, day, 0, 0, 0);
            start.set(Calendar.MILLISECOND, 0);
            customStartMs = start.getTimeInMillis();

            new DatePickerDialog(this, (v2, y2, m2, d2) -> {
                Calendar end = Calendar.getInstance(TimeFormatUtil.PKT_ZONE, Locale.US);
                end.set(y2, m2, d2, 23, 59, 59);
                end.set(Calendar.MILLISECOND, 999);
                customEndMs = Math.max(customStartMs, end.getTimeInMillis());
                selectMode(MODE_CUSTOM);
            }, year, month, day).show();
        }, pktCal.get(Calendar.YEAR), pktCal.get(Calendar.MONTH), pktCal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void renderReport() {
        long start, end;
        if (activeMode == MODE_TODAY) {
            start = TimeFormatUtil.startOfDayPkt(0);
            end = TimeFormatUtil.endOfDayPkt(0);
        } else if (activeMode == MODE_YESTERDAY) {
            start = TimeFormatUtil.startOfDayPkt(1);
            end = TimeFormatUtil.endOfDayPkt(1);
        } else if (activeMode == MODE_7D) {
            start = TimeFormatUtil.startOfDayPkt(6);
            end = TimeFormatUtil.endOfDayPkt(0);
        } else if (activeMode == MODE_30D) {
            start = TimeFormatUtil.startOfDayPkt(29);
            end = TimeFormatUtil.endOfDayPkt(0);
        } else {
            start = customStartMs;
            end = customEndMs;
        }

        tvRangeLabel.setText("Window (PKT): " + TimeFormatUtil.formatDateOnlyPkt(start)
                + " — " + TimeFormatUtil.formatDateOnlyPkt(end));

        List<Order> all = RepositoryProvider.orders(this).getAllOrders();
        List<Order> filtered = OrderReportCalculator.filterByDateRange(all, start, end);
        OrderReportCalculator.OrderReportSummary s = OrderReportCalculator.calculate(filtered, System.currentTimeMillis());

        tvKpis.setText(
                "Total Orders: " + s.totalOrders
                        + "\nDelivered Orders: " + s.completedOrders
                        + "\nActive / Pending Orders: " + s.pendingOrders
                        + "\nCancelled Orders: " + s.cancelledOrders
                        + "\nDelayed (> 45 min): " + s.delayedOrders
                        + "\nDelivered Revenue: " + CurrencyUtil.formatPkr(s.totalRevenue));

        tvTimings.setText(
                "Avg Order Confirm Time: " + formatOrDash(s.avgConfirmMs)
                        + "\nAvg Rider Accept Time: " + formatOrDash(s.avgRiderAcceptMs)
                        + "\nAvg Kitchen Prep Time: " + formatOrDash(s.avgKitchenPrepMs)
                        + "\nAvg Delivery Transit Time: " + formatOrDash(s.avgDeliveryMs)
                        + "\nAvg Total Completion Time: " + formatOrDash(s.avgTotalCompletionMs));

        if (filtered.isEmpty()) {
            tvOrderList.setText("No orders recorded in this PKT date range.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Order o : filtered) {
                sb.append("• ").append(o.getOrderId())
                        .append(" [").append(o.getOrderStatus()).append("] ")
                        .append(CurrencyUtil.formatPkr(o.getTotalAmount()))
                        .append(" — ").append(TimeFormatUtil.formatShortDateTimePkt(o.getPlacedTimestamp()))
                        .append("\n");
            }
            tvOrderList.setText(sb.toString().trim());
        }
    }

    private String formatOrDash(long ms) {
        return ms > 0L ? TimeFormatUtil.formatDuration(ms) : "No completed samples";
    }
}
