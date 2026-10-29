package vn.hcmute.edu.sia.service;

import java.util.List;
import java.util.UUID;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.dto.response.ChatContactResponse;
import vn.hcmute.edu.sia.dto.response.ChatConversationResponse;
import vn.hcmute.edu.sia.dto.response.ChatHistoryResponse;
import vn.hcmute.edu.sia.dto.response.DepartmentResponse;

public interface ChatService {
    void sendPrivateMessage(UUID senderId, SendChatMessageRequest request);

    List<ChatContactResponse> getContacts(UUID currentUserId);

    ChatHistoryResponse getHistory(UUID currentUserId, UUID contactId, int page, int size);

    List<ChatConversationResponse> getConversations(UUID currentUserId);

    List<DepartmentResponse> getDepartments();
}
