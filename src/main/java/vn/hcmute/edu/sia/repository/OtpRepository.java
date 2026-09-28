package vn.hcmute.edu.sia.repository;

import java.time.Duration;
import java.util.Optional;

import vn.hcmute.edu.sia.enums.OtpPurpose;

public interface OtpRepository {
    //Lưu OTP và thời gian hết hạn
    void saveOtp(String email, OtpPurpose purpose, String otp, Duration ttl);
    // Lấy OTP hiện tại
    Optional<String> findOtp(String email, OtpPurpose purpose);
    //Vô hiệu hóa OTP
    void deleteOtp(String email, OtpPurpose purpose);
    // Lưu thời để có thể gửi lại OTP
    void saveResendCooldown(String email, OtpPurpose purpose, Duration ttl);
    // Kiểm tra còn trong thời gian chờ gửi lại hay không
    boolean isResendCooldownActive(String email, OtpPurpose purpose);
    // Tăng số lần nhập OTP sai
    long incrementFailedAttempts(String email, OtpPurpose purpose, Duration ttl);
    // Xóa bộ đếm nhập sai
    void clearFailedAttempts(String email, OtpPurpose purpose);
    // Khóa việc xác thực OTP trong một khoảng thời gian
    void lockVerification(String email, OtpPurpose purpose, Duration ttl);
    // Kiểm tra việc xác thực OTP có đang bị khóa không
    boolean isVerificationLocked(String email, OtpPurpose purpose);
    // Thời gian còn lại để resend OTP
    Duration getResendCooldownRemaining(String email, OtpPurpose purpose);
    // Thời gian còn bị khóa xác thực OTP
    Duration getVerificationLockRemaining(String email, OtpPurpose purpose);
}
