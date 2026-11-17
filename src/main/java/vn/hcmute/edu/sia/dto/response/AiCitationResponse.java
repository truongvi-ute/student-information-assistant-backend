package vn.hcmute.edu.sia.dto.response;

import java.util.UUID;

public record AiCitationResponse(
        UUID documentId,
        String sourceTitle,
        Integer pageNumber,
        String chunkReference,
        String quotedContent,
        Double relevanceScore,
        Integer charStart,
        Integer charEnd
) {
    public AiCitationResponse(
            UUID documentId,
            String sourceTitle,
            Integer pageNumber,
            String chunkReference,
            String quotedContent,
            Double relevanceScore
    ) {
        this(documentId, sourceTitle, pageNumber, chunkReference, quotedContent, relevanceScore, null, null);
    }
}
