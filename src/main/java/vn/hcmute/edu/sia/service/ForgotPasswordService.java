package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.request.ResetPasswordRequest;
import vn.hcmute.edu.sia.dto.request.VerifyForgotPasswordOtpRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordOtpVerificationResponse;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;
import vn.hcmute.edu.sia.dto.response.PasswordResetResponse;

public interface ForgotPasswordService {
    ForgotPasswordResponse startForgotPassword(ForgotPasswordRequest request);
    ForgotPasswordResponse resendForgotPasswordOtp(String email);
    ForgotPasswordOtpVerificationResponse verifyForgotPasswordOtp(VerifyForgotPasswordOtpRequest request);
    PasswordResetResponse resetPassword(ResetPasswordRequest request);
}
