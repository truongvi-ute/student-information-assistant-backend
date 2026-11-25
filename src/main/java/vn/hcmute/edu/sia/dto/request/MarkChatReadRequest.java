package vn.hcmute.edu.sia.dto.request;

import java.time.Instant;
import java.util.UUID;

public record MarkChatReadRequest(
        UUID contactId,
        String lastMessageId,
        Instant lastReadAt
) {}
