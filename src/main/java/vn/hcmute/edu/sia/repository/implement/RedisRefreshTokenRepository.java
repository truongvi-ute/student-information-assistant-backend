package vn.hcmute.edu.sia.repository.implement;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import vn.hcmute.edu.sia.repository.RefreshTokenRepository;
import vn.hcmute.edu.sia.repository.RefreshTokenRepository.RefreshTokenRecord;
import vn.hcmute.edu.sia.repository.RefreshTokenRepository.RotationResult;

@Repository
public class RedisRefreshTokenRepository implements RefreshTokenRepository {

    private static final String TOKEN_PREFIX = "refresh:token:";
    private static final String FAMILY_PREFIX = "refresh:family:";
    private static final String ACCOUNT_FAMILY_PREFIX = "refresh:account:";

    private static final DefaultRedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[2], 'ACTIVE', 'PX', ARGV[1])
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            redis.call('SADD', KEYS[3], ARGV[4])
            redis.call('PEXPIRE', KEYS[3], ARGV[1])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value then return 0 end
            local status, accountId, familyId, absoluteExpiry = string.match(value, '^(%a+)|([^|]+)|([^|]+)|(%d+)$')
            if not status then return 0 end
            local familyKey = ARGV[1] .. familyId
            local familyStatus = redis.call('GET', familyKey)
            if status == 'USED' then
                local familyTtl = redis.call('PTTL', familyKey)
                if familyStatus == 'ACTIVE' and familyTtl > 0 then
                    redis.call('SET', familyKey, 'REVOKED', 'PX', familyTtl)
                end
                return 2
            end
            if status ~= 'ACTIVE' or familyStatus ~= 'ACTIVE' then return 0 end
            if accountId ~= ARGV[2] or familyId ~= ARGV[3] then return 0 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            if tonumber(absoluteExpiry) <= tonumber(ARGV[4]) then
                local familyTtl = redis.call('PTTL', familyKey)
                if familyTtl > 0 then redis.call('SET', familyKey, 'REVOKED', 'PX', familyTtl) end
                return 0
            end
            local tombstoneTtl = tonumber(absoluteExpiry) - tonumber(ARGV[4])
            redis.call('SET', KEYS[1], 'USED|' .. accountId .. '|' .. familyId .. '|' .. absoluteExpiry, 'PX', tombstoneTtl)
            redis.call('SET', KEYS[2], 'ACTIVE|' .. accountId .. '|' .. familyId .. '|' .. absoluteExpiry, 'PX', ARGV[5])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE_ALL_SCRIPT = new DefaultRedisScript<>("""
            local families = redis.call('SMEMBERS', KEYS[1])
            local revoked = 0
            for _, familyId in ipairs(families) do
                local familyKey = ARGV[1] .. familyId
                local ttl = redis.call('PTTL', familyKey)
                if ttl > 0 then
                    redis.call('SET', familyKey, 'REVOKED', 'PX', ttl)
                    revoked = revoked + 1
                end
            end
            return revoked
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE_FAMILY_SCRIPT = new DefaultRedisScript<>("""
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then return 0 end
            redis.call('SET', KEYS[1], 'REVOKED', 'PX', ttl)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean create(
            String tokenHash,
            UUID accountId,
            String familyId,
            long absoluteExpirationMillis,
            Duration idleTtl,
            Duration absoluteTtl
    ) {
        String tokenValue = activeValue(accountId, familyId, absoluteExpirationMillis);
        Long result = redisTemplate.execute(
                CREATE_SCRIPT,
                List.of(tokenKey(tokenHash), familyKey(familyId), accountFamiliesKey(accountId)),
                String.valueOf(absoluteTtl.toMillis()),
                tokenValue,
                String.valueOf(idleTtl.toMillis()),
                familyId
        );
        return Long.valueOf(1).equals(result);
    }

    @Override
    public Optional<RefreshTokenRecord> find(String tokenHash) {
        String value = redisTemplate.opsForValue().get(tokenKey(tokenHash));
        if (value == null) {
            return Optional.empty();
        }

        String[] fields = value.split("\\|", -1);
        if (fields.length != 4 || (!fields[0].equals("ACTIVE") && !fields[0].equals("USED"))) {
            return Optional.empty();
        }

        try {
            return Optional.of(new RefreshTokenRecord(
                    UUID.fromString(fields[1]),
                    fields[2],
                    Long.parseLong(fields[3]),
                    fields[0].equals("USED")
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public RotationResult rotate(
            String oldTokenHash,
            String newTokenHash,
            RefreshTokenRecord currentToken,
            Duration newTokenTtl,
            long nowMillis
    ) {
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(tokenKey(oldTokenHash), tokenKey(newTokenHash)),
                FAMILY_PREFIX,
                currentToken.accountId().toString(),
                currentToken.familyId(),
                String.valueOf(nowMillis),
                String.valueOf(newTokenTtl.toMillis())
        );

        if (Long.valueOf(1).equals(result)) {
            return RotationResult.ROTATED;
        }
        if (Long.valueOf(2).equals(result)) {
            return RotationResult.REUSED;
        }
        return RotationResult.INVALID;
    }

    @Override
    public void revokeFamily(String familyId) {
        redisTemplate.execute(
                REVOKE_FAMILY_SCRIPT,
                List.of(familyKey(familyId))
        );
    }

    @Override
    public void revokeAllForAccount(UUID accountId) {
        redisTemplate.execute(
                REVOKE_ALL_SCRIPT,
                List.of(accountFamiliesKey(accountId)),
                FAMILY_PREFIX
        );
    }

    private String activeValue(UUID accountId, String familyId, long absoluteExpirationMillis) {
        return "ACTIVE|" + accountId + "|" + familyId + "|" + absoluteExpirationMillis;
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_PREFIX + tokenHash;
    }

    private String familyKey(String familyId) {
        return FAMILY_PREFIX + familyId;
    }

    private String accountFamiliesKey(UUID accountId) {
        return ACCOUNT_FAMILY_PREFIX + accountId + ":families";
    }
}
