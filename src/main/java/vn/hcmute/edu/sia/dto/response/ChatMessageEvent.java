package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;

public record ChatMessageEvent(
        String messageId,
        String conversationId,
        String clientMessageId,
        String senderId,
        String recipientId,
        String message,
        String type,
        Instant createdAt
) {
    public static ChatMessageEvent chat(
            String messageId,
            String conversationId,
            String clientMessageId,
            String senderId,
            String recipientId,
            String message,
            Instant createdAt
    ) {
        return new ChatMessageEvent(
                messageId,
                conversationId,
                clientMessageId,
                senderId,
                recipientId,
                message,
                "CHAT",
                createdAt
        );
    }
}
