package com.derashanwari.rider.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.rider.R;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.util.CurrencyUtil;

import java.util.List;

public class RiderOrderAdapter extends RecyclerView.Adapter<RiderOrderAdapter.VH> {
    public interface OnRiderOrderClickListener {
        void onOrderClick(Order order);
    }

    private final List<Order> orders;
    private final OnRiderOrderClickListener listener;

    public RiderOrderAdapter(List<Order> orders, OnRiderOrderClickListener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    public void updateOrders(List<Order> newOrders) {
        orders.clear();
        orders.addAll(newOrders);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rider_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Order o = orders.get(position);
        h.orderId.setText(o.getOrderId() + " • " + CurrencyUtil.formatPkr(o.getTotalAmount()));
        h.status.setText(o.getOrderStatus());
        h.customer.setText(o.getCustomerName() + " • " + o.getCustomerPhone() + " • " + o.getPaymentMethod());
        h.address.setText("Deliver to: " + o.getDeliveryAddress());
        h.itemView.setOnClickListener(v -> listener.onOrderClick(o));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView orderId, status, customer, address;

        VH(@NonNull View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.tv_order_id);
            status = itemView.findViewById(R.id.tv_order_status);
            customer = itemView.findViewById(R.id.tv_order_customer);
            address = itemView.findViewById(R.id.tv_order_address);
        }
    }
}
