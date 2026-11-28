package vn.hcmute.edu.sia.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.hcmute.edu.sia.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("SELECT d FROM Document d JOIN FETCH d.uploadedBy ORDER BY d.createdAt DESC")
    List<Document> findAllWithUploaderOrderByCreatedAtDesc();

    @Query("""
            SELECT d FROM Document d
            JOIN FETCH d.uploadedBy u
            WHERE d.lifecycleStatus = vn.hcmute.edu.sia.enums.DocumentLifecycleStatus.ACTIVE
               OR u.id = :advisorId
            ORDER BY d.createdAt DESC
            """)
    List<Document> findVisibleForAdvisorOrderByCreatedAtDesc(@Param("advisorId") UUID advisorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") UUID id);

    List<Document> findByOriginalFilenameIn(Collection<String> originalFilenames);

    @Query("""
            SELECT d.id FROM Document d
            WHERE d.lifecycleStatus = vn.hcmute.edu.sia.enums.DocumentLifecycleStatus.ACTIVE
              AND d.ragStatus = vn.hcmute.edu.sia.enums.RagStatus.READY
            """)
    List<UUID> findRagEligibleDocumentIds();
}
