package vn.hcmute.edu.sia.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    boolean create(
            String tokenHash,
            UUID accountId,
            String familyId,
            long absoluteExpirationMillis,
            Duration idleTtl,
            Duration absoluteTtl
    );

    Optional<RefreshTokenRecord> find(String tokenHash);

    RotationResult rotate(
            String oldTokenHash,
            String newTokenHash,
            RefreshTokenRecord currentToken,
            Duration newTokenTtl,
            long nowMillis
    );

    void revokeFamily(String familyId);

    void revokeAllForAccount(UUID accountId);

    enum RotationResult {
        ROTATED,
        REUSED,
        INVALID
    }

    record RefreshTokenRecord(
            UUID accountId,
            String familyId,
            long absoluteExpirationMillis,
            boolean used
    ) {
    }
}
