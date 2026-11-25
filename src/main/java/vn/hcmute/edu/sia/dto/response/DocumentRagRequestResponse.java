package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.UUID;
import vn.hcmute.edu.sia.entity.DocumentRagRequest;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;

public record DocumentRagRequestResponse(
        UUID id,
        UUID documentId,
        String documentTitle,
        String currentDescription,
        boolean currentAllowDownload,
        DocumentLifecycleStatus documentLifecycleStatus,
        RagStatus documentRagStatus,
        String requestedByName,
        String reviewedByName,
        DocumentRequestType requestType,
        DocumentRequestStatus status,
        String proposedTitle,
        String proposedDescription,
        Boolean proposedAllowDownload,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt
) {
    public static DocumentRagRequestResponse from(DocumentRagRequest request) {
        return new DocumentRagRequestResponse(
                request.getId(),
                request.getDocument().getId(),
                request.getDocument().getTitle(),
                request.getDocument().getDescription(),
                request.getDocument().isAllowDownload(),
                request.getDocument().getLifecycleStatus(),
                request.getDocument().getRagStatus(),
                request.getRequestedByAdvisor().getFullName(),
                request.getReviewedByAdmin() != null ? request.getReviewedByAdmin().getFullName() : null,
                request.getRequestType(),
                request.getStatus(),
                request.getProposedTitle(),
                request.getProposedDescription(),
                request.getProposedAllowDownload(),
                request.getRejectionReason(),
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }
}
