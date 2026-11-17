package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AiConversationResponse(
        String conversationId,
        String title,
        UUID scopeMajorId,
        String lastMessage,
        Instant lastMessageAt,
        Instant createdAt
) {}
