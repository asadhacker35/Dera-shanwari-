package com.derashanwari.admin.repository;

import com.derashanwari.admin.model.ChatMessage;
import com.derashanwari.admin.model.GroupChatMember;
import com.derashanwari.admin.model.GroupChatMessage;

import java.util.List;

public interface ChatRepository {
    List<ChatMessage> getMessagesForOrder(String orderId);
    void sendMessage(ChatMessage message);

    List<GroupChatMessage> getGroupMessages();
    List<GroupChatMember> getGroupMembers();
    int getUnreadGroupCount();
    void sendGroupMessage(String senderId, String senderRole, String senderName,
                          String senderAvatarUrl, String messageText, String imageUrl);
    void deleteGroupMessage(String messageId);
    void markGroupRead(String uid, String role, String name, String avatarUrl);
    void setGroupOnline(String uid, String role, String name, String avatarUrl, boolean online);
}
