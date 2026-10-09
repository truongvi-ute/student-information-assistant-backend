package vn.hcmute.edu.sia.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email
) {
}
