package vn.hcmute.edu.sia.util;
// class util ngăn kế thừa và gi đè lại phương thức
public final class EmailNormalizer {
    //private tránh tạo ra object để class đảm bảo đúng chức năng là util
    private EmailNormalizer(){}
    //static phương thức thuộc về class không cần tạo object để dùng
    public static String normalize (String email){
        if (email == null || email.isBlank()){
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.trim().toLowerCase();
    }
}
