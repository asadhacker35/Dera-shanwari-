package com.derashanwari.admin.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderEvent;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.util.CurrencyUtil;
import com.derashanwari.admin.util.TimeFormatUtil;

import java.util.List;

public class AdminOrderAdapter extends RecyclerView.Adapter<AdminOrderAdapter.VH> {
    private static final String PAYLOAD_TIMER = "payload_timer";

    public interface OnOrderSelectListener {
        void onSelect(Order order);
    }

    private final List<Order> orders;
    private final OnOrderSelectListener listener;

    public AdminOrderAdapter(List<Order> orders, OnOrderSelectListener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    public void updateOrders(List<Order> newOrders) {
        orders.clear();
        if (newOrders != null) orders.addAll(newOrders);
        notifyDataSetChanged();
    }

    public void notifyTimersTick() {
        if (!orders.isEmpty()) {
            notifyItemRangeChanged(0, orders.size(), PAYLOAD_TIMER);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && PAYLOAD_TIMER.equals(payloads.get(0))) {
            bindTimer(h, orders.get(position));
            return;
        }
        onBindViewHolder(h, position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Order o = orders.get(position);
        h.orderId.setText(o.getOrderId() + " • " + CurrencyUtil.formatPkr(o.getTotalAmount()));
        h.status.setText(o.getOrderStatus());

        int pillColor = R.color.admin_orange;
        if (OrderStatus.DELIVERED.equalsIgnoreCase(o.getOrderStatus())) pillColor = R.color.admin_success;
        else if (OrderStatus.CANCELLED.equalsIgnoreCase(o.getOrderStatus())) pillColor = R.color.admin_danger;
        else if (OrderStatus.OUT_FOR_DELIVERY.equalsIgnoreCase(o.getOrderStatus())) pillColor = R.color.admin_info;
        h.status.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(h.itemView.getContext(), pillColor)));

        bindTimer(h, o);
        h.timePkt.setText("Placed: " + TimeFormatUtil.formatShortDateTimePkt(o.getPlacedTimestamp()) + " PKT");

        String custPhone = o.getCustomerPhone() != null ? o.getCustomerPhone() : "";
        h.customer.setText(o.getCustomerName() + (custPhone.isEmpty() ? "" : " (" + custPhone + ")"));

        int itemCount = o.getItems() != null ? o.getItems().size() : 0;
        String rider = o.getRiderName() != null && !o.getRiderName().isEmpty()
                ? "Rider: " + o.getRiderName() : "Rider Unassigned";
        h.meta.setText(itemCount + " item" + (itemCount == 1 ? "" : "s")
                + " • " + o.getPaymentMethod() + " • " + rider);

        if (o.getPrintCount() > 0) {
            h.printTag.setVisibility(View.VISIBLE);
            h.printTag.setText("Printed " + o.getPrintCount() + "x");
        } else {
            h.printTag.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> listener.onSelect(o));
    }

    private void bindTimer(VH h, Order o) {
        long placed = o.getPlacedTimestamp();
        if (OrderStatus.isCompletedOrCancelled(o.getOrderStatus())) {
            long doneAt = o.getTimestampForKey(OrderEvent.TS_DELIVERED);
            if (doneAt <= 0L) doneAt = o.getTimestampForKey(OrderEvent.TS_CANCELLED);
            if (doneAt <= 0L) doneAt = o.getUpdatedAt();
            long dur = Math.max(0L, doneAt - placed);
            h.timer.setText(o.getOrderStatus() + ": " + TimeFormatUtil.formatDuration(dur));
            h.timer.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.admin_success));
        } else {
            long now = System.currentTimeMillis();
            h.timer.setText("⏱ " + TimeFormatUtil.formatElapsedTimer(placed, now));
            long elapsedMin = (now - placed) / 60000L;
            int color = elapsedMin >= 45 ? R.color.admin_danger : R.color.admin_orange_dark;
            h.timer.setTextColor(ContextCompat.getColor(h.itemView.getContext(), color));
        }
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView orderId, status, timer, timePkt, customer, meta, printTag;

        VH(@NonNull View itemView) {
            super(itemView);
            orderId = itemView.findViewById(R.id.tv_order_id);
            status = itemView.findViewById(R.id.tv_order_status);
            timer = itemView.findViewById(R.id.tv_order_timer);
            timePkt = itemView.findViewById(R.id.tv_order_time_pkt);
            customer = itemView.findViewById(R.id.tv_order_customer);
            meta = itemView.findViewById(R.id.tv_order_meta);
            printTag = itemView.findViewById(R.id.tv_order_print_tag);
        }
    }
}
