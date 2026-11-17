package vn.hcmute.edu.sia.dto.response;

import java.util.UUID;

public record DocumentContentResponse(
        UUID documentId,
        String title,
        String originalFilename,
        String mimeType,
        boolean allowDownload,
        String markdownContent
) {}
