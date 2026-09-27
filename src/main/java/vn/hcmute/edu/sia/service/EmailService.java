package vn.hcmute.edu.sia.service;

public interface EmailService {
    void sendOtp(String recipientEmail, String otp);
}
