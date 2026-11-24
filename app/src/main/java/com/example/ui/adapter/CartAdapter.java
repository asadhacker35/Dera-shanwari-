package com.example.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.OrderItem;
import com.example.util.CurrencyUtil;
import com.example.util.ImageLoaderUtil;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
    public interface OnCartQuantityChangeListener {
        void onChangeQuantity(OrderItem item, int newQty);
    }

    private final List<OrderItem> items;
    private final OnCartQuantityChangeListener listener;

    public CartAdapter(List<OrderItem> items, OnCartQuantityChangeListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void updateItems(List<OrderItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        OrderItem item = items.get(position);
        h.name.setText(item.getProductName());
        h.price.setText(CurrencyUtil.formatPkr(item.getLineTotal()));
        h.qty.setText(String.valueOf(item.getQuantity()));
        ImageLoaderUtil.loadImage(h.image, item.getImageUrl(), item.getLocalDrawableName());

        h.minus.setOnClickListener(v -> listener.onChangeQuantity(item, item.getQuantity() - 1));
        h.plus.setOnClickListener(v -> listener.onChangeQuantity(item, item.getQuantity() + 1));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name, price, qty;
        final MaterialButton minus, plus;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.iv_cart_item);
            name = itemView.findViewById(R.id.tv_cart_name);
            price = itemView.findViewById(R.id.tv_cart_price);
            qty = itemView.findViewById(R.id.tv_cart_qty);
            minus = itemView.findViewById(R.id.btn_cart_minus);
            plus = itemView.findViewById(R.id.btn_cart_plus);
        }
    }
}
