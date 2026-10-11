package vn.hcmute.edu.sia.dto.response;

public record ForgotPasswordResponse(
        String message,
        long resendAvailableInSeconds
) {
}
