package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;

public interface ForgotPasswordService {
    ForgotPasswordResponse startForgotPassword(ForgotPasswordRequest request);
}
