package vn.hcmute.edu.sia.service;

import java.util.UUID;

import vn.hcmute.edu.sia.dto.internal.IssuedTokens;
import vn.hcmute.edu.sia.entity.Account;

public interface RefreshTokenService {
    IssuedTokens issue(Account account);

    IssuedTokens refresh(String refreshToken);

    void revoke(String refreshToken);

    void revokeAll(UUID accountId);
}
