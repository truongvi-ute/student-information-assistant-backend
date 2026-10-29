package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.UUID;
import vn.hcmute.edu.sia.enums.AccountRole;

public record ChatConversationResponse(
        String conversationId,
        UUID partnerId,
        String partnerName,
        AccountRole partnerRole,
        String partnerDepartment,
        String lastMessage,
        Instant lastMessageAt
) {}
