package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.request.ResetPasswordRequest;
import vn.hcmute.edu.sia.dto.request.VerifyForgotPasswordOtpRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordOtpVerificationResponse;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;
import vn.hcmute.edu.sia.dto.response.MessageResponse;

public interface ForgotPasswordService {
    ForgotPasswordResponse startForgotPassword(ForgotPasswordRequest request);

    ForgotPasswordOtpVerificationResponse verifyForgotPasswordOtp(
            VerifyForgotPasswordOtpRequest request
    );

    MessageResponse resetPassword(ResetPasswordRequest request);
}
