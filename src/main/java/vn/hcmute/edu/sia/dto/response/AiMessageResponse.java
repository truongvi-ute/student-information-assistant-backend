package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.List;
import vn.hcmute.edu.sia.enums.MessageSenderType;

public record AiMessageResponse(
        String messageId,
        MessageSenderType senderType,
        String content,
        List<AiCitationResponse> citations,
        Instant sentAt
) {}
