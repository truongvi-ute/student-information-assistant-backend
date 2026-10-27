package vn.hcmute.edu.sia.service;
import vn.hcmute.edu.sia.dto.request.RegisterRequest;
import vn.hcmute.edu.sia.dto.response.RegistrationOtpResponse;

public interface RegisterService {
    RegistrationOtpResponse startRegistration(RegisterRequest request);
    RegistrationOtpResponse resendRegistrationOtp(String email);
    void verifyRegistrationOtp(String email, String otp);
}
