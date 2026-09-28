package vn.hcmute.edu.sia.repository.implement;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import vn.hcmute.edu.sia.repository.LoginAttemptRepository;

@Repository
public class RedisLoginAttemptRepository implements LoginAttemptRepository {

    private static final String FAILED_ATTEMPTS_KEY_PREFIX = "auth:login:fail:";

    private static final String LOGIN_LOCK_KEY_PREFIX = "auth:login:lock:";

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long incrementFailedAttempts(String email, Duration ttl) {
        String key = failedAttemptsKey(email);

        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, ttl);
        }

        return attempts == null ? 0 : attempts;
    }

    @Override
    public void clearFailedAttempts(String email) {
        redisTemplate.delete(failedAttemptsKey(email));
    }

    @Override
    public void lockLogin(String email, Duration ttl) {
        redisTemplate.opsForValue().set(
            loginLockKey(email),
            "locked",
            ttl
        );
    }

    @Override
    public boolean isLoginLocked(String email) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(loginLockKey(email))
        );
    }

    @Override
    public Duration getLoginLockRemaining(String email) {
        return getRemainingTtl(loginLockKey(email));
    }

    private Duration getRemainingTtl(String key) {
        Long remainingSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (remainingSeconds == null || remainingSeconds <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(remainingSeconds);
    }

    private String failedAttemptsKey(String email) {
        return FAILED_ATTEMPTS_KEY_PREFIX + email;
    }

    private String loginLockKey(String email) {
        return LOGIN_LOCK_KEY_PREFIX + email;
    }
}