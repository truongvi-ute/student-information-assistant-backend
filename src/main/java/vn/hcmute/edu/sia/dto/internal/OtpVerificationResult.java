package vn.hcmute.edu.sia.dto.internal;

import vn.hcmute.edu.sia.enums.OtpVerificationStatus;

public record OtpVerificationResult(
        OtpVerificationStatus status,
        long lockRemainingSeconds
) {
}
