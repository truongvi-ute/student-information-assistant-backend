package vn.hcmute.edu.sia.service.implement;

import vn.hcmute.edu.sia.service.EmailService;

public class ConsoleEmailServiceImpl implements EmailService {

    @Override
    public void sendOtp(String recipientEmail, String otp) {
        System.out.println("=================================");
        System.out.println("Recipient: " + recipientEmail);
        System.out.println("OTP: " + otp);
        System.out.println("Valid for: 3 minutes");
        System.out.println("=================================");
    }
}
