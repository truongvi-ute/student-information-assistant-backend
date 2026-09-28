package vn.hcmute.edu.sia.dto.response;

public record OtpResendCooldownResponse(
        String message,
        long resendAvailableInSeconds) {}
