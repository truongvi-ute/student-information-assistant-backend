package vn.hcmute.edu.sia.dto.response;

public record ForgotPasswordOtpVerificationResponse(
        String message,
        String resetToken
) {
}
