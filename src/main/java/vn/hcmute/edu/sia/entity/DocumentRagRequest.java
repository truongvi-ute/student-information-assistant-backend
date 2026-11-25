package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;

@Entity
@Table(name = "document_rag_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentRagRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_rag_request_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_advisor_id", nullable = false)
    private Account requestedByAdvisor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private Account reviewedByAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private DocumentRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentRequestStatus status;

    @Column(name = "proposed_title", length = 255)
    private String proposedTitle;

    @Column(name = "proposed_description", columnDefinition = "TEXT")
    private String proposedDescription;

    @Column(name = "proposed_allow_download")
    private Boolean proposedAllowDownload;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public DocumentRagRequest(Document document, Account requestedByAdvisor) {
        this.document = Objects.requireNonNull(document, "Document must not be null");
        this.requestedByAdvisor = Objects.requireNonNull(
                requestedByAdvisor, "Advisor must not be null"
        );
        this.requestType = DocumentRequestType.ADD;
        this.status = DocumentRequestStatus.PENDING;
    }

    private DocumentRagRequest(
            Document document,
            Account requestedByAdvisor,
            DocumentRequestType requestType,
            String proposedTitle,
            String proposedDescription,
            Boolean proposedAllowDownload
    ) {
        this.document = Objects.requireNonNull(document, "Document must not be null");
        this.requestedByAdvisor = Objects.requireNonNull(
                requestedByAdvisor, "Advisor must not be null"
        );
        this.requestType = Objects.requireNonNull(requestType, "Request type must not be null");
        this.status = DocumentRequestStatus.PENDING;
        this.proposedTitle = proposedTitle;
        this.proposedDescription = proposedDescription;
        this.proposedAllowDownload = proposedAllowDownload;
    }

    public static DocumentRagRequest createAdd(Document document, Account requestedByAdvisor) {
        return new DocumentRagRequest(document, requestedByAdvisor);
    }

    public static DocumentRagRequest createUpdate(
            Document document,
            Account requestedByAdvisor,
            String proposedTitle,
            String proposedDescription,
            Boolean proposedAllowDownload
    ) {
        Objects.requireNonNull(document, "Document must not be null");
        Objects.requireNonNull(requestedByAdvisor, "Advisor must not be null");
        if (proposedTitle == null || proposedTitle.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        String normalizedTitle = proposedTitle.trim();
        if (normalizedTitle.length() > 255) {
            throw new IllegalArgumentException("Title must not exceed 255 characters");
        }
        if (proposedAllowDownload == null) {
            throw new IllegalArgumentException("Allow download must not be null");
        }
        String normalizedDescription = (proposedDescription == null || proposedDescription.isBlank())
                ? null
                : proposedDescription.trim();
        return new DocumentRagRequest(
                document,
                requestedByAdvisor,
                DocumentRequestType.UPDATE,
                normalizedTitle,
                normalizedDescription,
                proposedAllowDownload
        );
    }

    public static DocumentRagRequest createDelete(Document document, Account requestedByAdvisor) {
        return new DocumentRagRequest(
                document,
                requestedByAdvisor,
                DocumentRequestType.DELETE,
                null,
                null,
                null
        );
    }

    public void approve(Account admin) {
        if (status != DocumentRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be reviewed");
        }

        this.reviewedByAdmin = Objects.requireNonNull(admin, "Admin must not be null");
        this.status = DocumentRequestStatus.APPROVED;
        this.reviewedAt = Instant.now();
    }

    public void reject(Account admin, String reason) {
        if (status != DocumentRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be reviewed");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must not be blank");
        }

        this.reviewedByAdmin = Objects.requireNonNull(admin, "Admin must not be null");
        this.status = DocumentRequestStatus.REJECTED;
        this.rejectionReason = reason.trim();
        this.reviewedAt = Instant.now();
    }
}
