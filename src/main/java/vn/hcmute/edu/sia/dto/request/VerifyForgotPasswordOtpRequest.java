package vn.hcmute.edu.sia.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyForgotPasswordOtpRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "OTP must not be blank")
        @Pattern(
                regexp = "\\d{6}",
                message = "OTP must contain exactly 6 digits"
        )
        String otp
) {
}
