package vn.hcmute.edu.sia.service.implement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.RefreshTokenRepository;
import vn.hcmute.edu.sia.repository.RefreshTokenRepository.RefreshTokenRecord;
import vn.hcmute.edu.sia.repository.RefreshTokenRepository.RotationResult;
import vn.hcmute.edu.sia.security.JwtService;
import vn.hcmute.edu.sia.dto.internal.IssuedTokens;
import vn.hcmute.edu.sia.service.RefreshTokenService;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int CREATION_ATTEMPTS = 3;

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final Duration idleExpiration;
    private final Duration absoluteExpiration;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenServiceImpl(
            AccountRepository accountRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            @Value("${jwt.refresh-token-idle-expiration}") long idleExpirationMillis,
            @Value("${jwt.refresh-token-absolute-expiration}") long absoluteExpirationMillis
    ) {
        if (idleExpirationMillis <= 0 || absoluteExpirationMillis <= 0) {
            throw new IllegalArgumentException("Refresh token expirations must be positive.");
        }
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.idleExpiration = Duration.ofMillis(idleExpirationMillis);
        this.absoluteExpiration = Duration.ofMillis(absoluteExpirationMillis);
    }

    @Override
    public IssuedTokens issue(Account account) {
        Instant now = Instant.now();
        Instant absoluteExpiry = now.plus(absoluteExpiration);
        Duration cookieAndTokenTtl = min(idleExpiration, absoluteExpiration);

        for (int attempt = 0; attempt < CREATION_ATTEMPTS; attempt++) {
            String rawToken = generateToken();
            String familyId = UUID.randomUUID().toString();
            boolean created = refreshTokenRepository.create(
                    hash(rawToken),
                    account.getId(),
                    familyId,
                    absoluteExpiry.toEpochMilli(),
                    cookieAndTokenTtl,
                    absoluteExpiration
            );
            if (created) {
                return new IssuedTokens(
                        jwtService.generateAccessToken(account),
                        rawToken,
                        cookieAndTokenTtl
                );
            }
        }

        throw new IllegalStateException("Could not create a refresh token.");
    }

    @Override
    public IssuedTokens refresh(String refreshToken) {
        String oldTokenHash = hash(refreshToken);
        RefreshTokenRecord currentToken = refreshTokenRepository.find(oldTokenHash)
                .orElseThrow(this::unauthorized);

        Account account = accountRepository.findById(currentToken.accountId()).orElse(null);
        if (account == null || account.getAccessStatus() == AccountAccessStatus.BLOCKED) {
            refreshTokenRepository.revokeFamily(currentToken.familyId());
            throw unauthorized();
        }

        if (account.isPasswordChangeRequired()
                && (account.getRole() == AccountRole.ADMIN || account.getRole() == AccountRole.ADVISOR)) {
            refreshTokenRepository.revokeFamily(currentToken.familyId());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Password change is required before refreshing the session."
            );
        }

        long nowMillis = System.currentTimeMillis();
        long remainingMillis = currentToken.absoluteExpirationMillis() - nowMillis;
        if (remainingMillis <= 0) {
            refreshTokenRepository.revokeFamily(currentToken.familyId());
            throw unauthorized();
        }

        Duration nextTtl = min(idleExpiration, Duration.ofMillis(remainingMillis));
        String nextRawToken = generateToken();
        String nextTokenHash = hash(nextRawToken);
        String accessToken = jwtService.generateAccessToken(account);

        RotationResult result = refreshTokenRepository.rotate(
                oldTokenHash,
                nextTokenHash,
                currentToken,
                nextTtl,
                nowMillis
        );
        if (result != RotationResult.ROTATED) {
            throw unauthorized();
        }

        return new IssuedTokens(accessToken, nextRawToken, nextTtl);
    }

    @Override
    public void revoke(String refreshToken) {
        if (!isOpaqueTokenFormat(refreshToken)) {
            return;
        }
        refreshTokenRepository.find(hash(refreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.familyId()));
    }

    @Override
    public void revokeAll(UUID accountId) {
        refreshTokenRepository.revokeAllForAccount(accountId);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        if (!isOpaqueTokenFormat(token)) {
            throw unauthorized();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private boolean isOpaqueTokenFormat(String token) {
        if (token == null || token.length() != 43) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (!((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '_')) {
                return false;
            }
        }
        return true;
    }

    private Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired.");
    }
}
