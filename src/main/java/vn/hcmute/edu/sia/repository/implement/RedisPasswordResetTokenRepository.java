package vn.hcmute.edu.sia.repository.implement;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import vn.hcmute.edu.sia.repository.PasswordResetTokenRepository;

@Repository
public class RedisPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisPasswordResetTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String resetToken, String email, Duration ttl) {
        redisTemplate.opsForValue().set(
                resetTokenKey(resetToken),
                normalizeEmail(email),
                ttl
        );
    }

    @Override
    public Optional<String> findEmailByToken(String resetToken) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(resetTokenKey(resetToken))
        );
    }

    @Override
    public void delete(String resetToken) {
        redisTemplate.delete(resetTokenKey(resetToken));
    }

    private String resetTokenKey(String resetToken) {
        return "password-reset:token:" + resetToken;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
