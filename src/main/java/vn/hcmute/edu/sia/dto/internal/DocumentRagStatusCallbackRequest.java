package vn.hcmute.edu.sia.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DocumentRagStatusCallbackRequest(
        @NotBlank(message = "Status must not be blank")
        String status,
        @JsonProperty("total_chunks")
        Integer totalChunks,
        @JsonProperty("error_message")
        String errorMessage
) {}
