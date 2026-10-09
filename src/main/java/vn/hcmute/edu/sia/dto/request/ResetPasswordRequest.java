package vn.hcmute.edu.sia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token must not be blank")
        String resetToken,

        @NotBlank(message = "New password must not be blank")
        @Size(max = 72, message = "New password must not exceed 72 characters")
        String newPassword,

        @NotBlank(message = "Confirm password must not be blank")
        @Size(max = 72, message = "Confirm password must not exceed 72 characters")
        String confirmPassword
) {
}
