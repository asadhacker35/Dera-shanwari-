package com.derashanwari.user.repository;

import com.derashanwari.user.model.ChatMessage;
import com.derashanwari.user.model.GroupChatMember;
import com.derashanwari.user.model.GroupChatMessage;

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
