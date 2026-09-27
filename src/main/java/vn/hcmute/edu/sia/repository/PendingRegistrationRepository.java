package vn.hcmute.edu.sia.repository;
import java.time.Duration;
import java.util.Optional;
import vn.hcmute.edu.sia.dto.PendingRegistration;

public interface PendingRegistrationRepository {
    void save(String email, PendingRegistration pendingRegistration, Duration ttl);
    Optional<PendingRegistration> findByEmail(String email);
    void deleteByEmail(String email);
}
