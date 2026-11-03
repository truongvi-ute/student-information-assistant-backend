package vn.hcmute.edu.sia.dto.internal;

public record StoredDocumentFile(
        String storagePath,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String checksumSha256) {
}
