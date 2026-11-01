package vn.hcmute.edu.sia.service;

import java.util.UUID;

import vn.hcmute.edu.sia.dto.request.ChangePasswordRequest;
import vn.hcmute.edu.sia.dto.response.PasswordChangedResponse;

public interface ChangePasswordService {
    PasswordChangedResponse changePassword(UUID accountId, ChangePasswordRequest request);
}
