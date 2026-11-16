package vn.hcmute.edu.sia.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServiceChatRequest(
        String question,
        @JsonProperty("top_k") int topK,
        @JsonProperty("filename_filter") String filenameFilter
) {}
