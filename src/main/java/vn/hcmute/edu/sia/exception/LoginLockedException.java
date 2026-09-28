package vn.hcmute.edu.sia.exception;

public class LoginLockedException extends RuntimeException {

    private final long lockRemainingSeconds;

    public LoginLockedException(String message, long lockRemainingSeconds) {
        super(message);
        this.lockRemainingSeconds = lockRemainingSeconds;
    }

    public long getLockRemainingSeconds() {
        return lockRemainingSeconds;
    }
}