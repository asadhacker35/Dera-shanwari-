package com.example.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.Banner;
import com.example.util.ImageLoaderUtil;

import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {
    private final List<Banner> items;

    public BannerAdapter(List<Banner> items) {
        this.items = items;
    }

    public void updateBanners(List<Banner> updated) {
        items.clear();
        items.addAll(updated);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Banner b = items.get(position);
        h.title.setText(b.getTitle());
        h.subtitle.setText(b.getSubtitle());
        ImageLoaderUtil.loadImage(h.image, b.getImageUrl(), b.getLocalDrawableName());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title, subtitle;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.iv_banner);
            title = itemView.findViewById(R.id.tv_banner_title);
            subtitle = itemView.findViewById(R.id.tv_banner_subtitle);
        }
    }
}
