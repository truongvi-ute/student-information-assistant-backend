package vn.hcmute.edu.sia.dto.response;

public record OtpVerificationLockResponse(
        String message,
        long lockRemainingSeconds
) {
}