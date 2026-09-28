package vn.hcmute.edu.sia.repository;

import java.time.Duration;

public interface LoginAttemptRepository {

    long incrementFailedAttempts(String email, Duration ttl);

    void clearFailedAttempts(String email);

    void lockLogin(String email, Duration ttl);

    boolean isLoginLocked(String email);

    Duration getLoginLockRemaining(String email);
}