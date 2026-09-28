package vn.hcmute.edu.sia.dto.response;

import vn.hcmute.edu.sia.enums.AccountRole;

public record LoginResponse(
    String accessToken,
    AccountRole role,
    boolean mustChangePassword
) {
}