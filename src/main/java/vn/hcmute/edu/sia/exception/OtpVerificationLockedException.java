package vn.hcmute.edu.sia.exception;

public class OtpVerificationLockedException extends RuntimeException {

    private final long lockRemainingSeconds;

    public OtpVerificationLockedException(
            String message,
            long lockRemainingSeconds
    ) {
        super(message);
        this.lockRemainingSeconds = lockRemainingSeconds;
    }

    public long getLockRemainingSeconds() {
        return lockRemainingSeconds;
    }
}