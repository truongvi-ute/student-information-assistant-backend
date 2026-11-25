package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;

public record ChatMessageResponse(
        String messageId,
        String conversationId,
        String clientMessageId,
        String senderId,
        String recipientId,
        String message,
        Instant createdAt
) {}