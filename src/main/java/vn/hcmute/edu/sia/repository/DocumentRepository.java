package vn.hcmute.edu.sia.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import vn.hcmute.edu.sia.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("SELECT d FROM Document d JOIN FETCH d.uploadedBy ORDER BY d.createdAt DESC")
    List<Document> findAllWithUploaderOrderByCreatedAtDesc();
}
