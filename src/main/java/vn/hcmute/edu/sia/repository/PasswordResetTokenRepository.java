package vn.hcmute.edu.sia.repository;

import java.time.Duration;
import java.util.Optional;

public interface PasswordResetTokenRepository {
    void save(String resetToken, String email, Duration ttl);

    Optional<String> findEmailByToken(String resetToken);

    void delete(String resetToken);
}
