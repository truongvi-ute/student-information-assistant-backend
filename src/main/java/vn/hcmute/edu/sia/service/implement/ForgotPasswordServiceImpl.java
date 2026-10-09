package vn.hcmute.edu.sia.service.implement;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.stereotype.Service;

import vn.hcmute.edu.sia.dto.OtpVerificationResult;
import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.request.VerifyForgotPasswordOtpRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordOtpVerificationResponse;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.OtpPurpose;
import vn.hcmute.edu.sia.enums.OtpVerificationStatus;
import vn.hcmute.edu.sia.exception.OtpVerificationLockedException;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.PasswordResetTokenRepository;
import vn.hcmute.edu.sia.service.EmailService;
import vn.hcmute.edu.sia.service.ForgotPasswordService;
import vn.hcmute.edu.sia.service.OtpService;

@Service
public class ForgotPasswordServiceImpl implements ForgotPasswordService {

    private static final String FORGOT_PASSWORD_MESSAGE =
            "Da gui ma OTP ve email neu ton tai.";
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(10);
    private static final int RESET_TOKEN_BYTES = 32;

    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final SecureRandom secureRandom;

    public ForgotPasswordServiceImpl(
            AccountRepository accountRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            OtpService otpService,
            EmailService emailService
    ) {
        this.accountRepository = accountRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.otpService = otpService;
        this.emailService = emailService;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public ForgotPasswordResponse startForgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());

        Account account = accountRepository
                .findByEmail(email)
                .orElse(null);

        if (account == null || account.getAccessStatus() == AccountAccessStatus.BLOCKED) {
            return new ForgotPasswordResponse(
                    FORGOT_PASSWORD_MESSAGE,
                    0
            );
        }

        String otp = otpService.issueOtp(email, OtpPurpose.FORGOT_PASSWORD);
        emailService.sendOtp(email, otp);

        Duration resendCooldown =
                otpService.getResendCooldownRemaining(
                        email,
                        OtpPurpose.FORGOT_PASSWORD
                );

        return new ForgotPasswordResponse(
                FORGOT_PASSWORD_MESSAGE,
                resendCooldown.toSeconds()
        );
    }

    @Override
    public ForgotPasswordOtpVerificationResponse verifyForgotPasswordOtp(
            VerifyForgotPasswordOtpRequest request
    ) {
        String email = normalizeEmail(request.email());

        Account account = accountRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "OTP is invalid or expired."
                        )
                );

        if (account.getAccessStatus() == AccountAccessStatus.BLOCKED) {
            throw new IllegalArgumentException(
                    "OTP is invalid or expired."
            );
        }

        OtpVerificationResult otpResult =
                otpService.verifyOtp(
                        email,
                        OtpPurpose.FORGOT_PASSWORD,
                        request.otp()
                );

        if (otpResult.status() == OtpVerificationStatus.LOCKED) {
            throw new OtpVerificationLockedException(
                    "OTP verification is temporarily locked.",
                    otpResult.lockRemainingSeconds()
            );
        }

        if (otpResult.status() == OtpVerificationStatus.INVALID) {
            throw new IllegalArgumentException(
                    "OTP is invalid or expired."
            );
        }

        String resetToken = generateResetToken();

        passwordResetTokenRepository.save(
                resetToken,
                email,
                RESET_TOKEN_TTL
        );

        return new ForgotPasswordOtpVerificationResponse(
                "OTP verified.",
                resetToken
        );
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email must not be blank"
            );
        }

        return email.trim().toLowerCase();
    }

    private String generateResetToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
