package vn.hcmute.edu.sia.dto.internal;

import java.util.List;

public record AiServiceChatResponse(
        String question,
        String answer,
        List<AiServiceSourceCitation> sources
) {}
