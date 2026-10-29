package vn.hcmute.edu.sia.dto.request;

import java.util.UUID;

public record SendChatMessageRequest(
        UUID recipientId,
        String message
) {}