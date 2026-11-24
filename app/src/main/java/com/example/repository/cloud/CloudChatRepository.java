package com.example.repository.cloud;

import com.example.firebase.FirestoreSyncManager;
import com.example.model.ChatMessage;
import com.example.model.GroupChatMember;
import com.example.model.GroupChatMessage;
import com.example.repository.ChatRepository;

import java.util.ArrayList;
import java.util.List;

public class CloudChatRepository implements ChatRepository {
    private final FirestoreSyncManager sync;

    public CloudChatRepository(FirestoreSyncManager sync) {
        this.sync = sync;
    }

    @Override
    public List<ChatMessage> getMessagesForOrder(String orderId) {
        List<ChatMessage> res = new ArrayList<>();
        for (ChatMessage m : sync.getChats()) {
            if (orderId != null && orderId.equals(m.getOrderId())) res.add(m);
        }
        return res;
    }

    @Override
    public void sendMessage(ChatMessage message) {
        sync.sendChatMessage(message);
    }

    @Override
    public List<GroupChatMessage> getGroupMessages() {
        return sync.getGroupMessages();
    }

    @Override
    public List<GroupChatMember> getGroupMembers() {
        return sync.getGroupMembers();
    }

    @Override
    public int getUnreadGroupCount() {
        return sync.getUnreadGroupChatCount();
    }

    @Override
    public void sendGroupMessage(String senderId, String senderRole, String senderName,
                                 String senderAvatarUrl, String messageText, String imageUrl) {
        sync.sendGroupChatMessage(messageText, imageUrl);
    }

    @Override
    public void deleteGroupMessage(String messageId) {
        sync.deleteGroupChatMessage(messageId);
    }

    @Override
    public void markGroupRead(String uid, String role, String name, String avatarUrl) {
        sync.markGroupChatRead();
    }

    @Override
    public void setGroupOnline(String uid, String role, String name, String avatarUrl, boolean online) {
        sync.setGroupPresence(online);
    }
}
