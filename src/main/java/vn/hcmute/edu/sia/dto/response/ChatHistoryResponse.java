package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.List;

public record ChatHistoryResponse(
        String conversationId,
        List<ChatMessageResponse> messages,
        boolean hasNext,
        Instant partnerLastReadAt,
        Instant myLastReadAt
) {}