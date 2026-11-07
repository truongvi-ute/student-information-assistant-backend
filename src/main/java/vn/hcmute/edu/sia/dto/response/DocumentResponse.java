package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.UUID;

import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;

public record DocumentResponse(
        UUID documentId,
        String title,
        String description,
        boolean allowDownload,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        DocumentLifecycleStatus lifecycleStatus,
        RagStatus ragStatus,
        String uploadedByEmail,
        String uploadedByName,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDescription(),
                document.isAllowDownload(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getLifecycleStatus(),
                document.getRagStatus(),
                document.getUploadedBy() != null ? document.getUploadedBy().getEmail() : null,
                document.getUploadedBy() != null ? document.getUploadedBy().getFullName() : null,
                document.getArchivedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}

