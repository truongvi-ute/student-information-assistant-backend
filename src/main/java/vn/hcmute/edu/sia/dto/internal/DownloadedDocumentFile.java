package vn.hcmute.edu.sia.dto.internal;

public record DownloadedDocumentFile(
        String originalFilename,
        String mimeType,
        byte[] content,
        boolean allowDownload
) {}
