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
        Integer charEnd,
        String originalFilename
) {
    public AiCitationResponse(
            UUID documentId,
            String sourceTitle,
            Integer pageNumber,
            String chunkReference,
            String quotedContent,
            Double relevanceScore
    ) {
        this(documentId, sourceTitle, pageNumber, chunkReference, quotedContent, relevanceScore, null, null, null);
    }

    public AiCitationResponse(
            UUID documentId,
            String sourceTitle,
            Integer pageNumber,
            String chunkReference,
            String quotedContent,
            Double relevanceScore,
            Integer charStart,
            Integer charEnd
    ) {
        this(documentId, sourceTitle, pageNumber, chunkReference, quotedContent, relevanceScore, charStart, charEnd, null);
    }
}
