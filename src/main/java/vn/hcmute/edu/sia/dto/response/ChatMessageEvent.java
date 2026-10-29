package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;

public record ChatMessageEvent(
        String messageId,
        String senderId,
        String recipientId,
        String message,
        String type,
        Instant createdAt
) {}
