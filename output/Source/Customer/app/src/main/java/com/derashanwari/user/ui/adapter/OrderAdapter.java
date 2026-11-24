package com.derashanwari.user.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.user.R;
import com.derashanwari.user.model.Order;
import com.derashanwari.user.util.CurrencyUtil;
import com.derashanwari.user.util.TimeFormatUtil;

import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {
    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    private final List<Order> orders;
    private final OnOrderClickListener listener;

    public OrderAdapter(List<Order> orders, OnOrderClickListener listener) {
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Order o = orders.get(position);
        h.orderId.setText(o.getOrderId());
        h.status.setText(o.getOrderStatus());
        int count = o.getItems() != null ? o.getItems().size() : 0;
        h.summary.setText(count + " item(s) • " + o.getPaymentMethod()
                + (o.getRiderName() != null && !o.getRiderName().isEmpty() ? " • Rider: " + o.getRiderName() : ""));
        long ts = o.getPlacedTimestamp();
        h.date.setText("Placed: " + TimeFormatUtil.formatDateTimePkt(ts));
        h.total.setText(CurrencyUtil.formatPkr(o.getTotalAmount()));
        h.itemView.setOnClickListener(v -> listener.onOrderClick(o));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView orderId, status, summary, date, total;

        VH(@NonNull View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.tv_order_id);
            status = itemView.findViewById(R.id.tv_order_status);
            summary = itemView.findViewById(R.id.tv_order_summary);
            date = itemView.findViewById(R.id.tv_order_date);
            total = itemView.findViewById(R.id.tv_order_total);
        }
    }
}
