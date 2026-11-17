package vn.hcmute.edu.sia.dto.response;

import java.util.List;

public record AiChatbotHistoryResponse(
        List<AiMessageResponse> messages,
        boolean hasNext
) {}
