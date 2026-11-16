package vn.hcmute.edu.sia.service;

import java.util.List;
import java.util.UUID;
import vn.hcmute.edu.sia.dto.request.CreateAiConversationRequest;
import vn.hcmute.edu.sia.dto.request.SendAiMessageRequest;
import vn.hcmute.edu.sia.dto.response.AiChatbotHistoryResponse;
import vn.hcmute.edu.sia.dto.response.AiChatbotTurnResponse;
import vn.hcmute.edu.sia.dto.response.AiConversationResponse;

public interface AiChatbotService {
    AiConversationResponse createConversation(UUID studentId, CreateAiConversationRequest request);

    List<AiConversationResponse> getConversations(UUID studentId);

    AiChatbotHistoryResponse getMessages(UUID studentId, String conversationId, int page, int size);

    AiChatbotTurnResponse askQuestion(UUID studentId, String conversationId, SendAiMessageRequest request);

    void deleteConversation(UUID studentId, String conversationId);
}
