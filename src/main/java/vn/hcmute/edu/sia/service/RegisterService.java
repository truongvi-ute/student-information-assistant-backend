package vn.hcmute.edu.sia.service;
import vn.hcmute.edu.sia.dto.request.RegisterRequest;
import vn.hcmute.edu.sia.dto.response.OtpResendCooldownResponse;

public interface RegisterService {
    OtpResendCooldownResponse startRegistration(RegisterRequest request);
    void verifyRegistration(String email, String otp);
}
