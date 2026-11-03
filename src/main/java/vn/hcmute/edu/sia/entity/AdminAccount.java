package vn.hcmute.edu.sia.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;

@Entity
@Table(name = "admin_profiles")
@PrimaryKeyJoinColumn(name = "admin_id")
public class AdminAccount extends Account {

    protected AdminAccount() {
        super();
    }

    public AdminAccount(
            String fullName,
            String email,
            String passwordHash
    ) {
        super(
                fullName,
                email,
                passwordHash,
                AccountRole.ADMIN,
                AccountAccessStatus.ACTIVE,
                false
        );
    }
}

