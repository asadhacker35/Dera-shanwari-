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
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderStatus;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.ui.RiderDeliveryDetailActivity;
import com.derashanwari.rider.ui.adapter.RiderOrderAdapter;

import java.util.ArrayList;
import java.util.List;

public class RiderOrdersFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private static final String ARG_HISTORY = "arg_history";

    private boolean showHistory;
    private RiderOrderAdapter adapter;
    private TextView tvTitle, tvEmpty;

    public static RiderOrdersFragment newInstance(boolean history) {
        RiderOrdersFragment f = new RiderOrdersFragment();
        Bundle b = new Bundle();
        b.putBoolean(ARG_HISTORY, history);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHistory = getArguments() != null && getArguments().getBoolean(ARG_HISTORY, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rider_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvTitle = view.findViewById(R.id.tv_rider_orders_title);
        tvEmpty = view.findViewById(R.id.tv_rider_orders_empty);
        tvTitle.setText(showHistory ? "Delivery History" : "Assigned Deliveries");

        RecyclerView rv = view.findViewById(R.id.rv_rider_orders);
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
        refreshOrders();
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        refreshOrders();
    }

    private void refreshOrders() {
        Rider rider = RepositoryProvider.auth(requireContext()).getCurrentRider();
        if (rider == null && RepositoryProvider.isTestMode()) {
            rider = RepositoryProvider.riders(requireContext()).getRiderById("rider_1");
        }
        String riderId = rider != null ? rider.getId()
                : (!RepositoryProvider.isTestMode() ? FirebaseConfig.getAuth(requireContext()).getUid() : null);
        List<Order> filtered = new ArrayList<>();
        if (riderId != null) {
            for (Order o : RepositoryProvider.orders(requireContext()).getOrdersByRider(riderId)) {
                boolean done = OrderStatus.isCompletedOrCancelled(o.getOrderStatus());
                if (showHistory == done) filtered.add(o);
            }
        }
        adapter.updateOrders(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
