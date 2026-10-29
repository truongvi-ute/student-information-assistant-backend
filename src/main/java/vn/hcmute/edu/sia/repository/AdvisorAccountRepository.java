package vn.hcmute.edu.sia.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.hcmute.edu.sia.entity.AdvisorAccount;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;

public interface AdvisorAccountRepository extends JpaRepository<AdvisorAccount, UUID> {
    @Query("SELECT a FROM AdvisorAccount a JOIN FETCH a.department WHERE a.accessStatus = :status")
    List<AdvisorAccount> findAllWithDepartmentByAccessStatus(@Param("status") AccountAccessStatus status);

    @Query("SELECT a FROM AdvisorAccount a JOIN FETCH a.department WHERE a.id IN :ids")
    List<AdvisorAccount> findAllWithDepartmentByIdIn(@Param("ids") Iterable<UUID> ids);
}
