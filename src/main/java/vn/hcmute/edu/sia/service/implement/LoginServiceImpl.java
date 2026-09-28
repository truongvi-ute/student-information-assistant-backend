package vn.hcmute.edu.sia.service.implement;

import java.time.Duration;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import vn.hcmute.edu.sia.dto.request.LoginRequest;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.exception.LoginLockedException;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.LoginAttemptRepository;
import vn.hcmute.edu.sia.service.LoginService;
import vn.hcmute.edu.sia.dto.response.LoginResponse;
import vn.hcmute.edu.sia.security.JwtService;
@Service
public class LoginServiceImpl implements LoginService {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private static final Duration FAILED_ATTEMPTS_TTL =Duration.ofMinutes(5);
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(5);

    private final AccountRepository accountRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginServiceImpl(
        AccountRepository accountRepository,
        LoginAttemptRepository loginAttemptRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.accountRepository = accountRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        checkLoginLock(email);

        Account account = accountRepository
            .findByEmail(email)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Invalid email or password."
                )
            );

        if (account.getAccessStatus() == AccountAccessStatus.BLOCKED) {
            throw new IllegalStateException(
                "Account is blocked."
            );
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            handleFailedLogin(email);
            throw new IllegalArgumentException(
                "Invalid email or password."
            );
        }
        loginAttemptRepository.clearFailedAttempts(email);

        String accessToken = jwtService.generateAccessToken(account);

        return new LoginResponse(
            accessToken,
            account.getRole(),
            account.isPasswordChangeRequired()
        );
    }

    private void checkLoginLock(String email) {
        if (!loginAttemptRepository.isLoginLocked(email)) {
            return;
        }

        Duration lockRemaining = loginAttemptRepository.getLoginLockRemaining(email);

        throw new LoginLockedException(
            "Login is temporarily locked.",
            lockRemaining.toSeconds()
        );
    }

    private void handleFailedLogin(String email) {
        long failedAttempts = loginAttemptRepository.incrementFailedAttempts(email, FAILED_ATTEMPTS_TTL);

        if (failedAttempts < MAX_FAILED_ATTEMPTS) {
            return;
        }

        loginAttemptRepository.lockLogin(email, LOGIN_LOCK_TTL);

        loginAttemptRepository.clearFailedAttempts(email);

        Duration lockRemaining = loginAttemptRepository.getLoginLockRemaining(email);

        throw new LoginLockedException(
            "Login is temporarily locked.",
            lockRemaining.toSeconds()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}