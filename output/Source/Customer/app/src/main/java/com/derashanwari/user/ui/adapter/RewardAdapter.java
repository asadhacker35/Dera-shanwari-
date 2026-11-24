package com.derashanwari.user.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.user.R;
import com.derashanwari.user.model.Reward;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class RewardAdapter extends RecyclerView.Adapter<RewardAdapter.VH> {
    public interface OnRedeemClickListener {
        void onRedeem(Reward offer);
    }

    private final List<Reward> items;
    private final OnRedeemClickListener redeemListener;

    public RewardAdapter(List<Reward> items, OnRedeemClickListener redeemListener) {
        this.items = items;
        this.redeemListener = redeemListener;
    }

    public void updateItems(List<Reward> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reward, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Reward r = items.get(position);
        h.title.setText(r.getTitle());
        h.desc.setText(r.getDescription());
        if (r.isRedeemable() && redeemListener != null) {
            h.redeemBtn.setVisibility(View.VISIBLE);
            h.redeemBtn.setText(r.getMinPointsRequired() + " pts");
            h.redeemBtn.setOnClickListener(v -> redeemListener.onRedeem(r));
            h.points.setVisibility(View.GONE);
        } else {
            h.redeemBtn.setVisibility(View.GONE);
            h.points.setVisibility(View.VISIBLE);
            String prefix = r.getPoints() >= 0 ? "+" : "";
            h.points.setText(prefix + r.getPoints() + " pts");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, desc, points;
        final MaterialButton redeemBtn;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_reward_title);
            desc = itemView.findViewById(R.id.tv_reward_desc);
            points = itemView.findViewById(R.id.tv_reward_points);
            redeemBtn = itemView.findViewById(R.id.btn_redeem);
        }
    }
}
