package vn.hcmute.edu.sia.service.implement;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Service;

import vn.hcmute.edu.sia.enums.OtpPurpose;
import vn.hcmute.edu.sia.repository.OtpRepository;
import vn.hcmute.edu.sia.service.OtpService;

/**
 * OtpServiceIpml
 */
@Service
public class OtpServiceImpl implements OtpService{

    private static final Duration OTP_TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    private static final Duration FAILED_ATTEMPTS_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFICATION_LOCK_TTL = Duration.ofMinutes(5);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final OtpRepository otpRepository;
    private final SecureRandom secureRandom;
    
    public OtpServiceImpl(OtpRepository otpRepository) {
        this.otpRepository = otpRepository;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String issueOtp(String email, OtpPurpose purpose) {
        if (otpRepository.isVerificationLocked(email, purpose)) {
            throw new IllegalStateException(
                    "OTP verification is temporarily locked."
            );
        }
        if (otpRepository.isResendCooldownActive(email, purpose)) {
            throw new IllegalStateException(
                    "OTP cannot be resent yet."
            );
        }
        String otp = generateOtp();
        otpRepository.saveOtp(
                email,
                purpose,
                otp,
                OTP_TTL
        );
        otpRepository.saveResendCooldown(
                email,
                purpose,
                RESEND_COOLDOWN
        );
        return otp;
    }

    @Override
    public boolean verifyOtp(String email, OtpPurpose purpose, String otp) {
        if (otpRepository.isVerificationLocked(email, purpose)) {
            return false;
        }
        Optional<String> storedOtp = otpRepository.findOtp(email, purpose);

        if (storedOtp.isEmpty()) {
            return false;
        }

        if (!storedOtp.get().equals(otp)) {
            long failedAttempts = otpRepository.incrementFailedAttempts(
                            email,
                            purpose,
                            FAILED_ATTEMPTS_TTL
                    );
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                otpRepository.lockVerification(
                        email,
                        purpose,
                        VERIFICATION_LOCK_TTL
                );
                otpRepository.clearFailedAttempts(
                        email,
                        purpose
                );
            }
            return false;
        }
        otpRepository.deleteOtp(
                email,
                purpose
        );
        otpRepository.clearFailedAttempts(
                email,
                purpose
        );
        return true;
    }

    @Override
    public void invalidateOtp(String email, OtpPurpose purpose) {
        otpRepository.deleteOtp(
                email,
                purpose
        );
    }
    //helper
    private String generateOtp() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%06d", number);
    }    
}