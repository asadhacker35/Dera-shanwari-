package com.derashanwari.admin.ui.adapter;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.model.GroupChatMessage;
import com.derashanwari.admin.util.ImageLoaderUtil;
import com.derashanwari.admin.util.TimeFormatUtil;

import java.util.List;

public class GroupChatAdapter extends RecyclerView.Adapter<GroupChatAdapter.VH> {
    public interface OnMessageActionListener {
        void onDeleteMessage(GroupChatMessage message);
    }

    private final List<GroupChatMessage> messages;
    private final String currentUid;
    private final boolean canModerate;
    private final OnMessageActionListener listener;

    public GroupChatAdapter(List<GroupChatMessage> messages, String currentUid,
                            boolean canModerate, OnMessageActionListener listener) {
        this.messages = messages;
        this.currentUid = currentUid != null ? currentUid : "";
        this.canModerate = canModerate;
        this.listener = listener;
    }

    public void updateMessages(List<GroupChatMessage> updated) {
        messages.clear();
        if (updated != null) messages.addAll(updated);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_chat_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GroupChatMessage m = messages.get(position);
        boolean isOwn = currentUid.equals(m.getSenderId());

        h.bubble.setBackground(ContextCompat.getDrawable(h.itemView.getContext(),
                isOwn ? R.drawable.bg_glass_card_orange : R.drawable.bg_glass_card));

        h.sender.setText(m.getSenderName() + (isOwn ? " (You)" : ""));
        String role = m.getSenderRole().toUpperCase();
        h.roleBadge.setText(role);

        int badgeColorRes = R.color.admin_yellow;
        if ("ADMIN".equals(role)) badgeColorRes = R.color.admin_orange;
        else if ("RIDER".equals(role)) badgeColorRes = R.color.admin_info;
        h.roleBadge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(h.itemView.getContext(), badgeColorRes)));

        if (!m.getSenderAvatarUrl().isEmpty()) {
            ImageLoaderUtil.loadImage(h.avatar, m.getSenderAvatarUrl(), "ic_dera_emblem");
        } else {
            h.avatar.setImageResource(R.drawable.ic_dera_emblem);
        }

        if (m.hasImage()) {
            h.image.setVisibility(View.VISIBLE);
            ImageLoaderUtil.loadImage(h.image, m.getImageUrl(), "img_banner_shanwari");
            h.image.setOnClickListener(v -> showFullPhotoDialog(v, m.getImageUrl(), m.getSenderName()));
        } else {
            h.image.setVisibility(View.GONE);
            h.image.setOnClickListener(null);
        }

        if (!m.getMessage().isEmpty()) {
            h.message.setVisibility(View.VISIBLE);
            h.message.setText(m.getMessage());
        } else {
            h.message.setVisibility(View.GONE);
        }

        h.time.setText(TimeFormatUtil.formatShortDateTimePkt(m.getCreatedAt()) + " PKT");

        if (canModerate && listener != null) {
            h.deleteBtn.setVisibility(View.VISIBLE);
            h.deleteBtn.setOnClickListener(v -> listener.onDeleteMessage(m));
        } else {
            h.deleteBtn.setVisibility(View.GONE);
        }
    }

    private void showFullPhotoDialog(View anchor, String url, String title) {
        ImageView fullIv = new ImageView(anchor.getContext());
        fullIv.setAdjustViewBounds(true);
        fullIv.setPadding(16, 16, 16, 16);
        ImageLoaderUtil.loadImage(fullIv, url, "img_banner_shanwari");
        new AlertDialog.Builder(anchor.getContext())
                .setTitle("Shared by " + title)
                .setView(fullIv)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final LinearLayout bubble;
        final ImageView avatar, image;
        final TextView sender, roleBadge, message, time;
        final ImageButton deleteBtn;

        VH(@NonNull View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.card_chat_bubble);
            avatar = itemView.findViewById(R.id.iv_chat_avatar);
            image = itemView.findViewById(R.id.iv_chat_image);
            sender = itemView.findViewById(R.id.tv_chat_sender);
            roleBadge = itemView.findViewById(R.id.tv_chat_role_badge);
            message = itemView.findViewById(R.id.tv_chat_message);
            time = itemView.findViewById(R.id.tv_chat_time);
            deleteBtn = itemView.findViewById(R.id.btn_chat_delete);
        }
    }
}
