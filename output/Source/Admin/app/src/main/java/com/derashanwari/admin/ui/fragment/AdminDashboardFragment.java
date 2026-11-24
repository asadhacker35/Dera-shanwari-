package com.derashanwari.admin.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.NotificationItem;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.AdminOrderDetailActivity;
import com.derashanwari.admin.ui.AdminOrderReportActivity;
import com.derashanwari.admin.ui.GroupChatActivity;
import com.derashanwari.admin.ui.adapter.AdminOrderAdapter;
import com.derashanwari.admin.util.CurrencyUtil;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private TextView tvRevenue, tvTodaySales, tvTotalOrders, tvPending, tvProcessing,
            tvCompleted, tvCancelled, tvCustomersRiders, tvLiveAlert;
    private MaterialButton btnGroupChat;
    private View cardLiveAlert;
    private AdminOrderAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cardLiveAlert = view.findViewById(R.id.card_admin_live_alert);
        tvLiveAlert = view.findViewById(R.id.tv_admin_live_alert);
        btnGroupChat = view.findViewById(R.id.btn_dash_group_chat);

        btnGroupChat.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GroupChatActivity.class)));
        view.findViewById(R.id.btn_dash_order_report).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AdminOrderReportActivity.class)));

        view.findViewById(R.id.btn_admin_clear_alerts).setOnClickListener(v -> {
            if (!RepositoryProvider.isTestMode()) {
                RepositoryProvider.sync(requireContext()).markAllNotificationsRead();
            }
            refreshAlertBanner();
        });

        tvRevenue = view.findViewById(R.id.tv_kpi_revenue);
        tvTodaySales = view.findViewById(R.id.tv_kpi_today_sales);
        tvTotalOrders = view.findViewById(R.id.tv_kpi_total_orders);
        tvPending = view.findViewById(R.id.tv_kpi_pending);
        tvProcessing = view.findViewById(R.id.tv_kpi_processing);
        tvCompleted = view.findViewById(R.id.tv_kpi_completed);
        tvCancelled = view.findViewById(R.id.tv_kpi_cancelled);
        tvCustomersRiders = view.findViewById(R.id.tv_kpi_customers_riders);

        RecyclerView rv = view.findViewById(R.id.rv_dashboard_orders);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminOrderAdapter(new ArrayList<>(), order -> {
            Intent i = new Intent(requireContext(), AdminOrderDetailActivity.class);
            i.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, order.getOrderId());
            startActivity(i);
        });
        rv.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(requireContext()).addDataChangeListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(requireContext()).removeDataChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDashboard();
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        refreshDashboard();
    }

    private void refreshAlertBanner() {
        if (cardLiveAlert == null || RepositoryProvider.isTestMode()) {
            if (cardLiveAlert != null) cardLiveAlert.setVisibility(View.GONE);
            return;
        }
        List<NotificationItem> list = RepositoryProvider.sync(requireContext()).getNotifications();
        NotificationItem unread = null;
        for (NotificationItem n : list) {
            if (!n.isRead()) {
                unread = n;
                break;
            }
        }
        if (unread != null) {
            cardLiveAlert.setVisibility(View.VISIBLE);
            tvLiveAlert.setText(unread.getTitle() + "\n" + unread.getBody());
        } else {
            cardLiveAlert.setVisibility(View.GONE);
        }
    }

    private void refreshDashboard() {
        refreshAlertBanner();
        if (btnGroupChat != null) {
            int unreadChat = RepositoryProvider.chat(requireContext()).getUnreadGroupCount();
            btnGroupChat.setText(unreadChat > 0 ? "Group Chat (" + unreadChat + ")" : "Group Chat");
        }

        List<Order> all = RepositoryProvider.orders(requireContext()).getAllOrders();
        int pending = 0, processing = 0, completed = 0, cancelled = 0;
        double totalRev = 0.0, todaySales = 0.0;
        long oneDayAgo = System.currentTimeMillis() - 86400000L;

        for (Order o : all) {
            String st = o.getOrderStatus();
            if (OrderStatus.PENDING.equalsIgnoreCase(st)) {
                pending++;
            } else if (OrderStatus.DELIVERED.equalsIgnoreCase(st)) {
                completed++;
                totalRev += o.getTotalAmount();
                if (o.getPlacedTimestamp() >= oneDayAgo) todaySales += o.getTotalAmount();
            } else if (OrderStatus.CANCELLED.equalsIgnoreCase(st)) {
                cancelled++;
            } else {
                processing++;
                if (o.getPlacedTimestamp() >= oneDayAgo) todaySales += o.getTotalAmount();
            }
        }

        int custCount = RepositoryProvider.customers(requireContext()).getAllCustomers().size();
        int riderCount = RepositoryProvider.riders(requireContext()).getAllRiders().size();

        tvRevenue.setText(CurrencyUtil.formatPkr(totalRev));
        tvTodaySales.setText("Today's Sales: " + CurrencyUtil.formatPkr(todaySales));
        tvTotalOrders.setText(String.valueOf(all.size()));
        tvPending.setText(String.valueOf(pending));
        tvProcessing.setText(String.valueOf(processing));
        tvCompleted.setText(String.valueOf(completed));
        tvCancelled.setText(String.valueOf(cancelled));
        tvCustomersRiders.setText(custCount + " / " + riderCount);

        adapter.updateOrders(all.subList(0, Math.min(5, all.size())));
    }
}
