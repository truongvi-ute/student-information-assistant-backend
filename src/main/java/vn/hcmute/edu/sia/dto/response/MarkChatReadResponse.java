package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MarkChatReadResponse(
        String conversationId,
        UUID contactId,
        Instant lastReadAt,
        boolean updated
) {}
