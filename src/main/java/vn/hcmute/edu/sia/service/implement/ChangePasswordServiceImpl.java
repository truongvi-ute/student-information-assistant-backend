package vn.hcmute.edu.sia.service.implement;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hcmute.edu.sia.dto.request.ChangePasswordRequest;
import vn.hcmute.edu.sia.dto.response.PasswordChangedResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.service.ChangePasswordService;
import vn.hcmute.edu.sia.service.RefreshTokenService;
import vn.hcmute.edu.sia.validation.PasswordPolicy;

@Service
public class ChangePasswordServiceImpl implements ChangePasswordService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public ChangePasswordServiceImpl(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public PasswordChangedResponse changePassword(UUID accountId, ChangePasswordRequest request) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));

        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        PasswordPolicy.validate(request.newPassword());

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Confirm password does not match.");
        }

        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "New password must be different from current password."
            );
        }

        account.resetPassword(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);
        refreshTokenService.revokeAll(accountId);

        return new PasswordChangedResponse("Password changed successfully.");
    }
}
