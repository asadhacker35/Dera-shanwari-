package com.derashanwari.user.repository.demo;

import com.derashanwari.user.model.ChatMessage;
import com.derashanwari.user.model.GroupChatMember;
import com.derashanwari.user.model.GroupChatMessage;
import com.derashanwari.user.repository.ChatRepository;

import java.util.ArrayList;
import java.util.List;

public class DemoChatRepository implements ChatRepository {
    private final DemoDatabaseManager db;

    public DemoChatRepository(DemoDatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<ChatMessage> getMessagesForOrder(String orderId) {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : db.getState().chats) {
            if (orderId != null && orderId.equals(msg.getOrderId())) {
                result.add(msg);
            }
        }
        return result;
    }

    @Override
    public void sendMessage(ChatMessage message) {
        if (message.getId() == null || message.getId().trim().isEmpty()) {
            message.setId("msg_" + System.currentTimeMillis());
        }
        if (message.getTimestamp() <= 0) {
            message.setTimestamp(System.currentTimeMillis());
        }
        db.getState().chats.add(message);
        db.save();
    }

    @Override
    public List<GroupChatMessage> getGroupMessages() {
        return new ArrayList<>(db.getState().groupMessages);
    }

    @Override
    public List<GroupChatMember> getGroupMembers() {
        return new ArrayList<>(db.getState().groupMembers);
    }

    @Override
    public int getUnreadGroupCount() {
        long lastRead = db.getState().demoGroupLastReadAt;
        String uid = db.getState().demoCurrentUid;
        int c = 0;
        for (GroupChatMessage m : db.getState().groupMessages) {
            if ((uid == null || !uid.equals(m.getSenderId())) && m.getCreatedAt() > lastRead) {
                c++;
            }
        }
        return c;
    }

    @Override
    public void sendGroupMessage(String senderId, String senderRole, String senderName,
                                 String senderAvatarUrl, String messageText, String imageUrl) {
        long now = System.currentTimeMillis();
        String id = "gmsg_" + now + "_" + db.getState().groupMessages.size();
        db.getState().groupMessages.add(new GroupChatMessage(
                id,
                senderId != null ? senderId : "cust_1",
                senderName != null ? senderName : "Customer",
                senderRole != null ? senderRole : "CUSTOMER",
                senderAvatarUrl != null ? senderAvatarUrl : "",
                messageText != null ? messageText.trim() : "",
                imageUrl != null ? imageUrl.trim() : "",
                now
        ));
        db.getState().demoGroupLastReadAt = now;
        db.save();
    }

    @Override
    public void deleteGroupMessage(String messageId) {
        if (messageId == null) return;
        db.getState().groupMessages.removeIf(m -> messageId.equals(m.getId()));
        db.save();
    }

    @Override
    public void markGroupRead(String uid, String role, String name, String avatarUrl) {
        long now = System.currentTimeMillis();
        db.getState().demoGroupLastReadAt = now;
        setGroupOnline(uid, role, name, avatarUrl, true);
    }

    @Override
    public void setGroupOnline(String uid, String role, String name, String avatarUrl, boolean online) {
        if (uid == null || uid.isEmpty()) return;
        db.getState().demoCurrentUid = uid;
        long now = System.currentTimeMillis();
        long readAt = db.getState().demoGroupLastReadAt > 0L ? db.getState().demoGroupLastReadAt : now;
        for (GroupChatMember m : db.getState().groupMembers) {
            if (uid.equals(m.getUid())) {
                m.setOnline(online);
                m.setLastSeenAt(now);
                m.setLastReadAt(readAt);
                db.save();
                return;
            }
        }
        db.getState().groupMembers.add(new GroupChatMember(uid, name, role, avatarUrl, online, now, readAt));
        db.save();
    }
}
