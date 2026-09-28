package vn.hcmute.edu.sia.service;
import vn.hcmute.edu.sia.dto.request.RegisterRequest;

public interface RegisterService {
    void startRegistration(RegisterRequest request);
    void verifyRegistration(String email, String otp);
}
