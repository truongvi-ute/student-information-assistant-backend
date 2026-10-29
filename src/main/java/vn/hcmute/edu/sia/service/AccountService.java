package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.response.AccountProfileResponse;

import java.util.UUID;

public interface AccountService {
    AccountProfileResponse getMyProfile(UUID accountId);
}
