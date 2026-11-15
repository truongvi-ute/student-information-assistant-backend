package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.UUID;
import vn.hcmute.edu.sia.entity.DocumentRagRequest;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;

public record DocumentRagRequestResponse(
        UUID id,
        UUID documentId,
        String documentTitle,
        String requestedByName,
        DocumentRequestType requestType,
        DocumentRequestStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt
) {
    public static DocumentRagRequestResponse from(DocumentRagRequest request) {
        return new DocumentRagRequestResponse(
                request.getId(),
                request.getDocument().getId(),
                request.getDocument().getTitle(),
                request.getRequestedByAdvisor().getFullName(),
                request.getRequestType(),
                request.getStatus(),
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }
}
