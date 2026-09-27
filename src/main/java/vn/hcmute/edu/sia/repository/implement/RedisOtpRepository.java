package vn.hcmute.edu.sia.repository.implement;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import vn.hcmute.edu.sia.enums.OtpPurpose;
import vn.hcmute.edu.sia.repository.OtpRepository;

@Repository 
public class RedisOtpRepository implements OtpRepository {
    private final StringRedisTemplate redisTemplate;
    public RedisOtpRepository (StringRedisTemplate redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }
    @Override
    public void saveOtp(String email, OtpPurpose purpose, String otp, Duration ttl) {
        redisTemplate.opsForValue().set(
                                        otpKey(email, purpose), 
                                        otp, 
                                        ttl);
    }
    @Override
    public Optional<String> findOtp(String email, OtpPurpose purpose) {
        String otp = redisTemplate.opsForValue().get(otpKey(email, purpose));
        return Optional.ofNullable(otp);
    }
    @Override
    public void deleteOtp(String email, OtpPurpose purpose) {
        redisTemplate.delete(otpKey(email, purpose));
    }
    @Override
    public void saveResendCooldown(String email, OtpPurpose purpose, Duration ttl) {
        redisTemplate.opsForValue().set(resendKey(email, purpose), "1", ttl);
    }
    @Override
    public boolean isResendCooldownActive(String email, OtpPurpose purpose) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(resendKey(email, purpose)));
    }
    @Override
    public long incrementFailedAttempts(String email, OtpPurpose purpose, Duration ttl) {
        String key = failedAttemptsKey(email, purpose);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, ttl);
        }
        return attempts == null ? 0 : attempts;
    }
    @Override
    public void clearFailedAttempts(String email, OtpPurpose purpose) {
        redisTemplate.delete(failedAttemptsKey(email, purpose));
    }
    @Override
    public void lockVerification(String email, OtpPurpose purpose, Duration ttl) {
        redisTemplate.opsForValue().set(
                                        lockKey(email, purpose), 
                                        "1",
                                        ttl);
    }
    @Override
    public boolean isVerificationLocked(String email, OtpPurpose purpose) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(email, purpose)));
    }

    //helper
    private String otpKey (String email, OtpPurpose purpose){
        return "otp:%s:%s".formatted(purpose.name().toLowerCase(), normalizeEmail(email));
    }
    private String resendKey (String email, OtpPurpose purpose){
        return "otp:resend:%s:%s".formatted(purpose.name().toLowerCase(), normalizeEmail(email));
    }
    private String failedAttemptsKey (String email, OtpPurpose purpose){
        return "otp:attempts:%s:%s".formatted(purpose.name().toLowerCase(), normalizeEmail(email));
    }
    private String lockKey (String email, OtpPurpose purpose){
        return "otp:lock:%s:%s".formatted(purpose.name().toLowerCase(), normalizeEmail(email));
    }
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
