package vn.hcmute.edu.sia.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.entity.DocumentRagRequest;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DocumentRagRequestRepository
        extends JpaRepository<DocumentRagRequest, UUID> {

    List<DocumentRagRequest> findByRequestedByAdvisorOrderByCreatedAtDesc(Account advisor);
    List<DocumentRagRequest> findByStatusOrderByCreatedAtDesc(DocumentRequestStatus status);
    boolean existsByDocumentAndRequestTypeAndStatusIn(
            Document document,
            DocumentRequestType requestType,
            List<DocumentRequestStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DocumentRagRequest r WHERE r.id = :id")
    Optional<DocumentRagRequest> findByIdForUpdate(@Param("id") UUID id);
}