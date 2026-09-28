package vn.hcmute.edu.sia.service;

import java.time.Duration;

import vn.hcmute.edu.sia.dto.OtpVerificationResult;
import vn.hcmute.edu.sia.enums.OtpPurpose;

public interface OtpService {
    //Tạo ra mã OTP cho email với 1 mục đích cụ thể
    String issueOtp (String email, OtpPurpose purpose);
    //Xác minh xem OTP có hợp lệ không
    OtpVerificationResult verifyOtp (String email, OtpPurpose purpose, String otp);
    //Vô hiệu hóa OTP 
    void invalidateOtp (String email, OtpPurpose purpose); 
    // Thời gian còn lại để resend OTP   
    Duration getResendCooldownRemaining(String email, OtpPurpose purpose);
    // Thời gian còn lại bị khóa xác thực OTP
    Duration getVerificationLockRemaining(String email, OtpPurpose purpose);
}
