package vn.hcmute.edu.sia.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRegisterOtpRequest(

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email format is invalid")
        String email,

        @NotBlank(message = "OTP must not be blank")
        @Pattern(
                regexp = "\\d{6}",
                message = "OTP must contain exactly 6 digits"
        )
        String otp
) {
}