package com.derashanwari.admin.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.util.ImageLoaderUtil;

import java.util.List;

public class AdminCatalogAdapter extends RecyclerView.Adapter<AdminCatalogAdapter.VH> {
    public static class CatalogRow {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String meta;
        public final String imageUrl;
        public final String localDrawableName;

        public CatalogRow(String id, String title, String subtitle, String meta,
                          String imageUrl, String localDrawableName) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.meta = meta;
            this.imageUrl = imageUrl;
            this.localDrawableName = localDrawableName;
        }
    }

    public interface OnCatalogActionListener {
        void onEdit(String id);
        void onDelete(String id);
    }

    private final List<CatalogRow> rows;
    private final OnCatalogActionListener listener;

    public AdminCatalogAdapter(List<CatalogRow> rows, OnCatalogActionListener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    public void updateRows(List<CatalogRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_catalog, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CatalogRow row = rows.get(position);
        h.title.setText(row.title);
        h.subtitle.setText(row.subtitle);
        h.meta.setText(row.meta);
        ImageLoaderUtil.loadImage(h.thumb, row.imageUrl, row.localDrawableName);
        h.edit.setOnClickListener(v -> listener.onEdit(row.id));
        h.delete.setOnClickListener(v -> listener.onDelete(row.id));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView title, subtitle, meta;
        final ImageButton edit, delete;

        VH(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.iv_thumb);
            title = itemView.findViewById(R.id.tv_title);
            subtitle = itemView.findViewById(R.id.tv_subtitle);
            meta = itemView.findViewById(R.id.tv_meta);
            edit = itemView.findViewById(R.id.btn_edit);
            delete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
