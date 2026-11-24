package com.derashanwari.admin.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class AdminPeopleAdapter extends RecyclerView.Adapter<AdminPeopleAdapter.VH> {
    public static class PersonRow {
        public final String id;
        public final String name;
        public final String details;
        public final boolean active;

        public PersonRow(String id, String name, String details, boolean active) {
            this.id = id;
            this.name = name;
            this.details = details;
            this.active = active;
        }
    }

    public interface OnPersonActionListener {
        void onToggleActive(String id, boolean currentActive);
        void onClick(String id);
    }

    private final List<PersonRow> rows;
    private final OnPersonActionListener listener;

    public AdminPeopleAdapter(List<PersonRow> rows, OnPersonActionListener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    public void updateRows(List<PersonRow> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_person, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PersonRow row = rows.get(position);
        h.name.setText(row.name);
        h.details.setText(row.details);
        h.toggleBtn.setText(row.active ? "Active" : "Inactive");
        h.toggleBtn.setOnClickListener(v -> listener.onToggleActive(row.id, row.active));
        h.itemView.setOnClickListener(v -> listener.onClick(row.id));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, details;
        final MaterialButton toggleBtn;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_person_name);
            details = itemView.findViewById(R.id.tv_person_details);
            toggleBtn = itemView.findViewById(R.id.btn_toggle_active);
        }
    }
}
