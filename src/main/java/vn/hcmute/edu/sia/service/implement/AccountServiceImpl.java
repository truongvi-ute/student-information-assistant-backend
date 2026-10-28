package vn.hcmute.edu.sia.service.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.response.AccountProfileResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.service.AccountService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public AccountProfileResponse getMyProfile(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        return new AccountProfileResponse(
                account.getId(),
                account.getFullName(),
                account.getEmail(),
                account.getRole()
        );
    }
}
