package vn.hcmute.edu.sia.service.implement;

import java.time.Duration;

import org.springframework.stereotype.Service;

import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.OtpPurpose;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.service.EmailService;
import vn.hcmute.edu.sia.service.ForgotPasswordService;
import vn.hcmute.edu.sia.service.OtpService;

@Service
public class ForgotPasswordServiceImpl implements ForgotPasswordService {

    private static final String FORGOT_PASSWORD_MESSAGE =
            "Da gui ma OTP ve email neu ton tai.";

    private final AccountRepository accountRepository;
    private final OtpService otpService;
    private final EmailService emailService;

    public ForgotPasswordServiceImpl(
            AccountRepository accountRepository,
            OtpService otpService,
            EmailService emailService
    ) {
        this.accountRepository = accountRepository;
        this.otpService = otpService;
        this.emailService = emailService;
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

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email must not be blank"
            );
        }

        return email.trim().toLowerCase();
    }
}
