package com.derashanwari.user.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.user.R;
import com.derashanwari.user.model.Category;
import com.derashanwari.user.util.ImageLoaderUtil;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
    public interface OnCategorySelectedListener {
        void onCategorySelected(Category category);
    }

    private final List<Category> categories;
    private final OnCategorySelectedListener listener;
    private final boolean gridMode;
    private String selectedCategoryId = "ALL";

    public CategoryAdapter(List<Category> categories, OnCategorySelectedListener listener) {
        this(categories, false, listener);
    }

    public CategoryAdapter(List<Category> categories, boolean gridMode, OnCategorySelectedListener listener) {
        this.categories = categories;
        this.gridMode = gridMode;
        this.listener = listener;
    }

    public void setSelectedCategoryId(String id) {
        this.selectedCategoryId = id;
        notifyDataSetChanged();
    }

    public void updateCategories(List<Category> updated) {
        categories.clear();
        categories.addAll(updated);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = gridMode ? R.layout.item_category_grid : R.layout.item_category;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Category c = categories.get(position);
        h.name.setText(c.getName());
        ImageLoaderUtil.loadImage(h.icon, c.getImageUrl(), c.getLocalDrawableName());

        boolean selected = c.getId().equals(selectedCategoryId);
        int bgColor = ContextCompat.getColor(h.itemView.getContext(),
                selected ? R.color.brand_orange : R.color.card_light);
        int strokeColor = ContextCompat.getColor(h.itemView.getContext(),
                selected ? R.color.brand_white : R.color.glass_card_stroke);
        int txtColor = ContextCompat.getColor(h.itemView.getContext(),
                selected ? R.color.brand_white : R.color.text_primary);
        h.card.setCardBackgroundColor(bgColor);
        h.card.setStrokeColor(strokeColor);
        h.name.setTextColor(txtColor);

        h.itemView.setOnClickListener(v -> {
            selectedCategoryId = c.getId();
            notifyDataSetChanged();
            listener.onCategorySelected(c);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView name;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card_category);
            icon = itemView.findViewById(R.id.iv_category);
            name = itemView.findViewById(R.id.tv_category_name);
        }
    }
}
