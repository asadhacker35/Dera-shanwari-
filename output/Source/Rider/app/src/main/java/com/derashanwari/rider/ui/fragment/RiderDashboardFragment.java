package com.derashanwari.rider.ui.fragment;

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

import com.derashanwari.rider.R;
import com.derashanwari.rider.firebase.FirebaseConfig;
import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.model.NotificationItem;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderStatus;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.ui.RiderDeliveryDetailActivity;
import com.derashanwari.rider.ui.adapter.RiderOrderAdapter;
import com.derashanwari.rider.util.CurrencyUtil;

import java.util.ArrayList;
import java.util.List;

public class RiderDashboardFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private TextView tvWelcome, tvEarnings, tvActive, tvCompleted, tvLiveAlert;
    private View cardLiveAlert;
    private RiderOrderAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rider_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cardLiveAlert = view.findViewById(R.id.card_rider_live_alert);
        tvLiveAlert = view.findViewById(R.id.tv_rider_live_alert);
        view.findViewById(R.id.btn_rider_clear_alerts).setOnClickListener(v -> {
            if (!RepositoryProvider.isTestMode()) {
                RepositoryProvider.sync(requireContext()).markAllNotificationsRead();
            }
            refreshAlertBanner();
        });

        tvWelcome = view.findViewById(R.id.tv_rider_welcome);
        tvEarnings = view.findViewById(R.id.tv_rider_earnings);
        tvActive = view.findViewById(R.id.tv_active_deliveries);
        tvCompleted = view.findViewById(R.id.tv_completed_deliveries);

        RecyclerView rv = view.findViewById(R.id.rv_dashboard_assigned);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RiderOrderAdapter(new ArrayList<>(), order -> {
            Intent i = new Intent(requireContext(), RiderDeliveryDetailActivity.class);
            i.putExtra(RiderDeliveryDetailActivity.EXTRA_ORDER_ID, order.getOrderId());
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
        Rider rider = RepositoryProvider.auth(requireContext()).getCurrentRider();
        if (rider == null && RepositoryProvider.isTestMode()) {
            rider = RepositoryProvider.riders(requireContext()).getRiderById("rider_1");
        }
        String riderId = rider != null ? rider.getId()
                : (!RepositoryProvider.isTestMode() ? FirebaseConfig.getAuth(requireContext()).getUid() : null);

        tvWelcome.setText("Assalam-o-Alaikum, " + (rider != null ? rider.getName() : "Rider Partner"));
        tvEarnings.setText(CurrencyUtil.formatPkr(rider != null ? rider.getTotalEarnings() : 0.0));
        tvCompleted.setText(String.valueOf(rider != null ? rider.getTotalDeliveries() : 0));

        List<Order> active = new ArrayList<>();
        if (riderId != null) {
            for (Order o : RepositoryProvider.orders(requireContext()).getOrdersByRider(riderId)) {
                if (!OrderStatus.isCompletedOrCancelled(o.getOrderStatus())) {
                    active.add(o);
                }
            }
        }
        tvActive.setText(String.valueOf(active.size()));
        adapter.updateOrders(active);
    }
}
