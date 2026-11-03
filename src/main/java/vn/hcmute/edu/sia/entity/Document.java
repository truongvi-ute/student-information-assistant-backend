package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;

@Entity
@Getter
@Table(name = "documents")
public class Document {

    private static final long MAX_SIZE_BYTES = 20_971_520L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private Account uploadedBy;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "allow_download", nullable = false)
    private boolean allowDownload = true;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false, unique = true, columnDefinition = "TEXT")
    private String storagePath;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private DocumentLifecycleStatus lifecycleStatus = DocumentLifecycleStatus.DRAFT;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
        // Constructor required by JPA.
    }

    public Document(
            Account uploadedBy,
            String title,
            String description,
            boolean allowDownload,
            String originalFilename,
            String storagePath,
            String mimeType,
            long sizeBytes,
            String checksumSha256
    ) {
        if (uploadedBy == null || uploadedBy.getRole() != AccountRole.ADVISOR) {
            throw new IllegalArgumentException("Only an advisor can upload a document");
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Document size must be between 1 byte and 20 MiB");
        }
        if (checksumSha256 == null || !checksumSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("SHA-256 checksum must contain exactly 64 hexadecimal characters");
        }

        this.uploadedBy = uploadedBy;
        this.title = requireText(title, "Title", 255);
        this.description = normalizeOptionalText(description);
        this.allowDownload = allowDownload;
        this.originalFilename = requireText(originalFilename, "Original filename", 255);
        this.storagePath = requireText(storagePath, "Storage path");
        this.mimeType = requireText(mimeType, "MIME type", 100);
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256.toLowerCase(Locale.ROOT);
    }

    private String requireText(String value, String fieldName, int maxLength) {
        String normalized = requireText(value, fieldName);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
