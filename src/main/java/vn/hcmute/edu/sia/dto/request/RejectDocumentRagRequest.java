package vn.hcmute.edu.sia.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectDocumentRagRequest(
        @NotBlank(message = "Rejection reason is required")
        String reason
) {
}