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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DocumentRagRequestRepository
        extends JpaRepository<DocumentRagRequest, UUID> {

    @Query("""
            SELECT r FROM DocumentRagRequest r
            LEFT JOIN FETCH r.document
            JOIN FETCH r.requestedByAdvisor
            LEFT JOIN FETCH r.reviewedByAdmin
            WHERE r.requestedByAdvisor = :advisor
            ORDER BY r.createdAt DESC
            """)
    List<DocumentRagRequest> findByRequestedByAdvisorOrderByCreatedAtDesc(@Param("advisor") Account advisor);

    @Query("""
            SELECT r FROM DocumentRagRequest r
            JOIN FETCH r.document
            JOIN FETCH r.requestedByAdvisor
            LEFT JOIN FETCH r.reviewedByAdmin
            WHERE r.status = :status
            ORDER BY r.createdAt DESC
            """)
    List<DocumentRagRequest> findByStatusOrderByCreatedAtDesc(@Param("status") DocumentRequestStatus status);
    boolean existsByDocumentAndStatus(Document document, DocumentRequestStatus status);

    @Modifying
    @Query("""
            UPDATE DocumentRagRequest r SET r.deletedDocumentId = :documentId,
                r.deletedDocumentTitle = :title, r.document = null
            WHERE r.document = :document AND r.status = :status
            """)
    void keepDeletedDocumentHistory(@Param("document") Document document,
                                    @Param("status") DocumentRequestStatus status,
                                    @Param("documentId") UUID documentId, @Param("title") String title);
    boolean existsByDocumentAndRequestTypeAndStatusIn(
            Document document,
            DocumentRequestType requestType,
            List<DocumentRequestStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DocumentRagRequest r WHERE r.id = :id")
    Optional<DocumentRagRequest> findByIdForUpdate(@Param("id") UUID id);
}
