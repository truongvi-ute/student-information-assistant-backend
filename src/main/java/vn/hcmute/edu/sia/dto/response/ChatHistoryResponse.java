package vn.hcmute.edu.sia.dto.response;

import java.util.List;

public record ChatHistoryResponse(
        List<ChatMessageResponse> messages,
        boolean hasNext
) {}