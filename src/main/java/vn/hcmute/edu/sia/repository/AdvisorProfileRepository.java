package vn.hcmute.edu.sia.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.hcmute.edu.sia.entity.AdvisorProfile;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;

public interface AdvisorProfileRepository extends JpaRepository<AdvisorProfile, UUID> {

    @Query("""
            SELECT profile
            FROM AdvisorProfile profile
            JOIN FETCH profile.account account
            JOIN FETCH profile.department
            WHERE account.role = :role
              AND account.accessStatus = :status
            """)
    List<AdvisorProfile> findAllWithAccountAndDepartmentByAccountAccessStatus(
            @Param("role") AccountRole role,
            @Param("status") AccountAccessStatus status
    );

    @Query("""
            SELECT profile
            FROM AdvisorProfile profile
            JOIN FETCH profile.account
            JOIN FETCH profile.department
            WHERE profile.account.id IN :accountIds
              AND profile.account.role = :role
            """)
    List<AdvisorProfile> findAllWithAccountAndDepartmentByAccountIdIn(
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("role") AccountRole role
    );
}
