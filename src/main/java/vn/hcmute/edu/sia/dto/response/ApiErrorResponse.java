package vn.hcmute.edu.sia.dto.response;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        OffsetDateTime timestamp) {}