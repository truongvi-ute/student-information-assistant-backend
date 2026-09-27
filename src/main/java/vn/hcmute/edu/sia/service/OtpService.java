package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.enums.OtpPurpose;

public interface OtpService {
    //Tạo ra mã OTP cho email với 1 mục đích cụ thể
    String issueOtp (String email, OtpPurpose purpose);
    //Xác minh xem OTP có hợp lệ không
    boolean verifyOtp (String email, OtpPurpose purpose, String otp);
    //Vô hiệu hóa OTP 
    void invalidateOtp (String email, OtpPurpose purpose);    
}
