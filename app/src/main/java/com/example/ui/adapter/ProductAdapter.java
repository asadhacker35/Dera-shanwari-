package com.example.ui.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.Product;
import com.example.util.CurrencyUtil;
import com.example.util.ImageLoaderUtil;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    public interface OnProductActionListener {
        void onProductClick(Product product);
        void onQuickAddClick(Product product);
    }

    private final List<Product> items;
    private final boolean horizontal;
    private final OnProductActionListener listener;

    public ProductAdapter(List<Product> items, boolean horizontal, OnProductActionListener listener) {
        this.items = items;
        this.horizontal = horizontal;
        this.listener = listener;
    }

    public void updateItems(List<Product> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = horizontal ? R.layout.item_product_horizontal : R.layout.item_product;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Product p = items.get(position);
        h.name.setText(p.getName());
        h.price.setText(CurrencyUtil.formatPkr(p.getEffectivePrice()));
        ImageLoaderUtil.loadImage(h.image, p.getImageUrl(), p.getLocalDrawableName());

        if (h.category != null) h.category.setText(p.getCategoryName());
        if (h.desc != null) h.desc.setText(p.getDescription());
        if (h.oldPrice != null) {
            if (p.hasDiscount()) {
                h.oldPrice.setVisibility(View.VISIBLE);
                h.oldPrice.setText(CurrencyUtil.formatPkr(p.getPrice()));
                h.oldPrice.setPaintFlags(h.oldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                h.oldPrice.setVisibility(View.GONE);
            }
        }
        if (h.discountBadge != null) {
            if (p.hasDiscount()) {
                h.discountBadge.setVisibility(View.VISIBLE);
                h.discountBadge.setText(p.getDiscountPercent() + "% OFF");
            } else {
                h.discountBadge.setVisibility(View.GONE);
            }
        }

        h.itemView.setOnClickListener(v -> listener.onProductClick(p));
        h.quickAdd.setOnClickListener(v -> listener.onQuickAddClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name, price, category, desc, oldPrice, discountBadge;
        final MaterialButton quickAdd;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.iv_product);
            name = itemView.findViewById(R.id.tv_product_name);
            price = itemView.findViewById(R.id.tv_product_price);
            category = itemView.findViewById(R.id.tv_product_category);
            desc = itemView.findViewById(R.id.tv_product_desc);
            oldPrice = itemView.findViewById(R.id.tv_product_old_price);
            discountBadge = itemView.findViewById(R.id.tv_discount_badge);
            quickAdd = itemView.findViewById(R.id.btn_quick_add);
        }
    }
}
