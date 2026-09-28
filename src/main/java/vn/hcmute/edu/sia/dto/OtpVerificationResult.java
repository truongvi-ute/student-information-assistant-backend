package vn.hcmute.edu.sia.dto;

import vn.hcmute.edu.sia.enums.OtpVerificationStatus;

public record OtpVerificationResult(
        OtpVerificationStatus status,
        long lockRemainingSeconds
) {
}
