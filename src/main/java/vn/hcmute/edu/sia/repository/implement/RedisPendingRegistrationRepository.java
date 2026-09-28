package vn.hcmute.edu.sia.repository.implement;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.hcmute.edu.sia.dto.PendingRegistration;
import vn.hcmute.edu.sia.repository.PendingRegistrationRepository;

@Repository
public class RedisPendingRegistrationRepository implements PendingRegistrationRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisPendingRegistrationRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String email, PendingRegistration pendingRegistration, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(pendingRegistration);

            redisTemplate.opsForValue().set(
                    pendingRegistrationKey(email),
                    json,
                    ttl
            );
        } catch (JacksonException  e) {
            throw new IllegalStateException(
                    "Failed to serialize pending registration",
                    e
            );
        }
    }

    @Override
    public Optional<PendingRegistration> findByEmail(String email) {

        String json = redisTemplate.opsForValue()
                .get(pendingRegistrationKey(email));

        if (json == null) {
            return Optional.empty();
        }

        try {
            PendingRegistration pendingRegistration =
                    objectMapper.readValue(
                            json,
                            PendingRegistration.class
                    );

            return Optional.of(pendingRegistration);

        } catch (JacksonException  e) {
            throw new IllegalStateException(
                    "Failed to deserialize pending registration",
                    e
            );
        }
    }

    @Override
    public void deleteByEmail(String email) {
        redisTemplate.delete(pendingRegistrationKey(email));
    }
    //helper
    private String pendingRegistrationKey(String email) {
        return "register:pending:" + normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}