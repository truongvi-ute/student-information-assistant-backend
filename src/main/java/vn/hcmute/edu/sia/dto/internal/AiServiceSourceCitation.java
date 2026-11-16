package vn.hcmute.edu.sia.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServiceSourceCitation(
        String filename,
        @JsonProperty("page_number") Integer pageNumber,
        @JsonProperty("chunk_index") int chunkIndex,
        double score,
        String snippet
) {}
