package vn.hcmute.edu.sia.dto.response;

public record AiChatbotTurnResponse(
        String conversationId,
        String title,
        AiMessageResponse studentMessage,
        AiMessageResponse aiMessage
) {}
