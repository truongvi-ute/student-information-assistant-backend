package vn.hcmute.edu.sia.dto.internal;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServiceIngestRequest(
        @JsonProperty("document_id") String documentId,
        @JsonProperty("file_key") String fileKey,
        String filename,
        @JsonProperty("callback_url") String callbackUrl,
        Map<String, Object> metadata
) {}
