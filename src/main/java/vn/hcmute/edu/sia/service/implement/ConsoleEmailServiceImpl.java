package vn.hcmute.edu.sia.service.implement;

import org.springframework.stereotype.Service;

import vn.hcmute.edu.sia.service.EmailService;

@Service
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
