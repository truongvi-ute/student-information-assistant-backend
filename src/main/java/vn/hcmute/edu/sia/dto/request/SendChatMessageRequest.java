package vn.hcmute.edu.sia.dto.request;

import java.util.UUID;

public record SendChatMessageRequest(
        UUID recipientId,
        String message,
        String clientMessageId
) {
    public SendChatMessageRequest(UUID recipientId, String message) {
        this(recipientId, message, null);
    }
}