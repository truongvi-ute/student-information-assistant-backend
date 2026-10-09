package vn.hcmute.edu.sia.validation;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 6;
    private static final int MAX_LENGTH = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Password must not be blank."
            );
        }

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be between 6 and 72 characters."
            );
        }

        if (!containsLetter(password) || !containsDigit(password)) {
            throw new IllegalArgumentException(
                    "Password must contain at least one letter and one number."
            );
        }
    }

    private static boolean containsLetter(String password) {
        return password
                .chars()
                .anyMatch(Character::isLetter);
    }

    private static boolean containsDigit(String password) {
        return password
                .chars()
                .anyMatch(Character::isDigit);
    }
}
