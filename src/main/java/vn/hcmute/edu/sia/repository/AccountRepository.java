package vn.hcmute.edu.sia.repository;
import vn.hcmute.edu.sia.dto.response.AccountProfileResponse;
import vn.hcmute.edu.sia.entity.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.hcmute.edu.sia.enums.AccountRole;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByEmail(String email);
    Optional<Account> findByEmail(String email);
    List<Account> findAllByRole(AccountRole role);
    Optional<Account> findById(UUID accountId);
}
