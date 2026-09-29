package vn.hcmute.edu.sia.dto.response;

public record LoginLockResponse(
    String message,
    long lockRemainingSeconds
) {
}