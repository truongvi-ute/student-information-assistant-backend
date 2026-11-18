package vn.hcmute.edu.sia.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServiceIngestResponse(
        String status,
        @JsonProperty("document_id") String documentId,
        String message
) {}
