package vn.hcmute.edu.sia.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServiceSourceCitation(
        @JsonProperty("document_id") String documentId,
        String filename,
        @JsonProperty("page_number") Integer pageNumber,
        @JsonProperty("chunk_index") int chunkIndex,
        @JsonProperty("char_start") Integer charStart,
        @JsonProperty("char_end") Integer charEnd,
        double score,
        String snippet,
        String content
) {
    public AiServiceSourceCitation(
            String filename,
            Integer pageNumber,
            int chunkIndex,
            double score,
            String snippet
    ) {
        this(null, filename, pageNumber, chunkIndex, null, null, score, snippet, null);
    }
}
