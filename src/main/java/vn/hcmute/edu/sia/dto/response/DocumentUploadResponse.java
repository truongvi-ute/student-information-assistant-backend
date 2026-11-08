package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.util.UUID;

import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;

public record DocumentUploadResponse(
        UUID documentId,
        String title,
        String description,
        boolean allowDownload,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        DocumentLifecycleStatus lifecycleStatus,
        Instant createdAt
) {
    public static DocumentUploadResponse from(Document document) {
        return new DocumentUploadResponse(
                document.getId(),
                document.getTitle(),
                document.getDescription(),
                document.isAllowDownload(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getLifecycleStatus(),
                document.getCreatedAt()
        );
    }
}
