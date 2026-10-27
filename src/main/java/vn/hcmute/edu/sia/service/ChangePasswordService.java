package vn.hcmute.edu.sia.service;

import java.util.UUID;

import vn.hcmute.edu.sia.dto.request.ChangePasswordRequest;
import vn.hcmute.edu.sia.dto.response.MessageResponse;

public interface ChangePasswordService {
    MessageResponse changePassword(UUID accountId, ChangePasswordRequest request);
}
