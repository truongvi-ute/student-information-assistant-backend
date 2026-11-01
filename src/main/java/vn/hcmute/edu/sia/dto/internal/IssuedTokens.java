package vn.hcmute.edu.sia.dto.internal;

import java.time.Duration;

public record IssuedTokens(
        String accessToken,
        String refreshToken,
        Duration refreshTokenMaxAge) {
}
