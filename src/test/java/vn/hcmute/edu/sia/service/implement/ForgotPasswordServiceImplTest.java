package vn.hcmute.edu.sia.service.implement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.hcmute.edu.sia.dto.OtpVerificationResult;
import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.request.ResetPasswordRequest;
import vn.hcmute.edu.sia.dto.request.VerifyForgotPasswordOtpRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordOtpVerificationResponse;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;
import vn.hcmute.edu.sia.entity.StudentAccount;
import vn.hcmute.edu.sia.enums.OtpPurpose;
import vn.hcmute.edu.sia.enums.OtpVerificationStatus;
import vn.hcmute.edu.sia.exception.OtpVerificationLockedException;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.PasswordResetTokenRepository;
import vn.hcmute.edu.sia.service.EmailService;
import vn.hcmute.edu.sia.service.OtpService;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ForgotPasswordServiceImpl forgotPasswordService;

    @Test
    void startForgotPasswordSendsOtpWhenEmailExists() {
        StudentAccount account = activeAccount();
        when(accountRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(account));
        when(otpService.issueOtp("student@example.com", OtpPurpose.FORGOT_PASSWORD))
                .thenReturn("123456");
        when(otpService.getResendCooldownRemaining(
                "student@example.com",
                OtpPurpose.FORGOT_PASSWORD
        )).thenReturn(Duration.ofSeconds(60));

        ForgotPasswordResponse response =
                forgotPasswordService.startForgotPassword(
                        new ForgotPasswordRequest(" Student@Example.com ")
                );

        assertEquals("Da gui ma OTP ve email neu ton tai.", response.message());
        assertEquals(60, response.resendAvailableInSeconds());
        verify(emailService).sendOtp("student@example.com", "123456");
    }

    @Test
    void startForgotPasswordDoesNotRevealMissingEmail() {
        when(accountRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        ForgotPasswordResponse response =
                forgotPasswordService.startForgotPassword(
                        new ForgotPasswordRequest("missing@example.com")
                );

        assertEquals("Da gui ma OTP ve email neu ton tai.", response.message());
        assertEquals(0, response.resendAvailableInSeconds());
        verify(otpService, never()).issueOtp(any(), any());
        verify(emailService, never()).sendOtp(any(), any());
    }

    @Test
    void verifyForgotPasswordOtpRejectsInvalidOtp() {
        when(accountRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(activeAccount()));
        when(otpService.verifyOtp(
                "student@example.com",
                OtpPurpose.FORGOT_PASSWORD,
                "000000"
        )).thenReturn(
                new OtpVerificationResult(OtpVerificationStatus.INVALID, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> forgotPasswordService.verifyForgotPasswordOtp(
                        new VerifyForgotPasswordOtpRequest(
                                "student@example.com",
                                "000000"
                        )
                )
        );

        verify(passwordResetTokenRepository, never()).save(any(), any(), any());
    }

    @Test
    void verifyForgotPasswordOtpThrowsWhenLocked() {
        when(accountRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(activeAccount()));
        when(otpService.verifyOtp(
                "student@example.com",
                OtpPurpose.FORGOT_PASSWORD,
                "000000"
        )).thenReturn(
                new OtpVerificationResult(OtpVerificationStatus.LOCKED, 300)
        );

        OtpVerificationLockedException exception =
                assertThrows(
                        OtpVerificationLockedException.class,
                        () -> forgotPasswordService.verifyForgotPasswordOtp(
                                new VerifyForgotPasswordOtpRequest(
                                        "student@example.com",
                                        "000000"
                                )
                        )
                );

        assertEquals(300, exception.getLockRemainingSeconds());
    }

    @Test
    void verifyForgotPasswordOtpIssuesResetTokenWhenOtpIsValid() {
        when(accountRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(activeAccount()));
        when(otpService.verifyOtp(
                "student@example.com",
                OtpPurpose.FORGOT_PASSWORD,
                "123456"
        )).thenReturn(
                new OtpVerificationResult(OtpVerificationStatus.VERIFIED, 0)
        );

        ForgotPasswordOtpVerificationResponse response =
                forgotPasswordService.verifyForgotPasswordOtp(
                        new VerifyForgotPasswordOtpRequest(
                                "student@example.com",
                                "123456"
                        )
                );

        assertEquals("OTP verified.", response.message());
        assertNotNull(response.resetToken());
        assertFalse(response.resetToken().isBlank());
        verify(passwordResetTokenRepository)
                .save(eq(response.resetToken()), eq("student@example.com"), any());
    }

    @Test
    void resetPasswordRejectsInvalidResetToken() {
        when(passwordResetTokenRepository.findEmailByToken("bad-token"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> forgotPasswordService.resetPassword(
                        new ResetPasswordRequest(
                                "bad-token",
                                "abc123",
                                "abc123"
                        )
                )
        );
    }

    @Test
    void resetPasswordRejectsInvalidPasswordPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> forgotPasswordService.resetPassword(
                        new ResetPasswordRequest(
                                "reset-token",
                                "abcdef",
                                "abcdef"
                        )
                )
        );

        verify(passwordResetTokenRepository, never()).findEmailByToken(any());
    }

    @Test
    void resetPasswordRejectsMismatchedConfirmPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> forgotPasswordService.resetPassword(
                        new ResetPasswordRequest(
                                "reset-token",
                                "abc123",
                                "abc124"
                        )
                )
        );

        verify(passwordResetTokenRepository, never()).findEmailByToken(any());
    }

    @Test
    void resetPasswordRejectsCurrentPasswordReuse() {
        StudentAccount account = activeAccount();
        when(passwordResetTokenRepository.findEmailByToken("reset-token"))
                .thenReturn(Optional.of("student@example.com"));
        when(accountRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("abc123", "old-hash"))
                .thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> forgotPasswordService.resetPassword(
                        new ResetPasswordRequest(
                                "reset-token",
                                "abc123",
                                "abc123"
                        )
                )
        );

        verify(accountRepository, never()).save(any());
        verify(passwordResetTokenRepository, never()).delete(any());
    }

    @Test
    void resetPasswordStoresNewHashAndConsumesToken() {
        StudentAccount account = activeAccount();
        when(passwordResetTokenRepository.findEmailByToken("reset-token"))
                .thenReturn(Optional.of("student@example.com"));
        when(accountRepository.findByEmail("student@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("abc123", "old-hash"))
                .thenReturn(false);
        when(passwordEncoder.encode("abc123"))
                .thenReturn("new-hash");

        forgotPasswordService.resetPassword(
                new ResetPasswordRequest(
                        "reset-token",
                        "abc123",
                        "abc123"
                )
        );

        ArgumentCaptor<StudentAccount> accountCaptor =
                ArgumentCaptor.forClass(StudentAccount.class);

        verify(accountRepository).save(accountCaptor.capture());
        assertEquals("new-hash", accountCaptor.getValue().getPasswordHash());
        assertFalse(accountCaptor.getValue().isPasswordChangeRequired());
        verify(passwordResetTokenRepository).delete("reset-token");
        verify(otpService).invalidateOtp(
                "student@example.com",
                OtpPurpose.FORGOT_PASSWORD
        );
    }

    private StudentAccount activeAccount() {
        return new StudentAccount(
                "Student User",
                "student@example.com",
                "old-hash",
                null,
                null,
                null,
                null
        );
    }
}
