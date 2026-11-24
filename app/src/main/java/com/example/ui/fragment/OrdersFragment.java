package com.example.ui.fragment;

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

import com.example.MainActivity;
import com.example.R;
import com.example.firebase.FirestoreSyncManager;
import com.example.model.Customer;
import com.example.model.NotificationItem;
import com.example.model.Order;
import com.example.repository.RepositoryProvider;
import com.example.ui.OrderDetailActivity;
import com.example.ui.adapter.OrderAdapter;

import java.util.ArrayList;
import java.util.List;

public class OrdersFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private OrderAdapter adapter;
    private TextView tvEmpty, tvAlert;
    private View cardAlert;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvEmpty = view.findViewById(R.id.tv_orders_empty);
        cardAlert = view.findViewById(R.id.card_customer_alert);
        tvAlert = view.findViewById(R.id.tv_customer_alert);
        view.findViewById(R.id.btn_mark_alerts_read).setOnClickListener(v -> {
            if (!RepositoryProvider.isTestMode()) {
                RepositoryProvider.sync(requireContext()).markAllNotificationsRead();
            }
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshOrdersBadge();
            }
            refreshOrders();
        });

        RecyclerView rv = view.findViewById(R.id.rv_orders);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OrderAdapter(new ArrayList<>(), order -> {
            Intent i = new Intent(requireContext(), OrderDetailActivity.class);
            i.putExtra(OrderDetailActivity.EXTRA_ORDER_ID, order.getOrderId());
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
        refreshOrders();
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        refreshOrders();
    }

    private void refreshOrders() {
        Customer c = RepositoryProvider.auth(requireContext()).getCurrentCustomer();
        List<Order> orders = c != null
                ? RepositoryProvider.orders(requireContext()).getOrdersByCustomer(c.getId())
                : new ArrayList<>();
        adapter.updateOrders(orders);
        tvEmpty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);

        if (!RepositoryProvider.isTestMode()) {
            List<NotificationItem> unread = new ArrayList<>();
            for (NotificationItem n : RepositoryProvider.sync(requireContext()).getNotifications()) {
                if (!n.isRead()) unread.add(n);
            }
            if (!unread.isEmpty()) {
                NotificationItem latest = unread.get(0);
                cardAlert.setVisibility(View.VISIBLE);
                tvAlert.setText(latest.getTitle() + " — " + latest.getBody());
            } else {
                cardAlert.setVisibility(View.GONE);
            }
        }
    }
}
