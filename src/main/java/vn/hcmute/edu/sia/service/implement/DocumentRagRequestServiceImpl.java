package vn.hcmute.edu.sia.service.implement;

import java.util.LinkedHashMap;
import java.io.IOException;
import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.exception.DocumentStorageException;
import vn.hcmute.edu.sia.service.DocumentStorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestRequest;
import vn.hcmute.edu.sia.dto.response.DocumentRagRequestResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.entity.DocumentRagRequest;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DocumentRagRequestRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.AiServiceClient;
import vn.hcmute.edu.sia.service.DocumentRagRequestService;
import vn.hcmute.edu.sia.service.DocumentRagStatusService;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import vn.hcmute.edu.sia.dto.request.CreateDocumentUpdateRequest;

@Service
@RequiredArgsConstructor
public class DocumentRagRequestServiceImpl implements DocumentRagRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRagRequestServiceImpl.class);
    private static final String ONE_PENDING_INDEX_NAME = "uq_document_rag_requests_one_pending";

    private final AccountRepository accountRepository;
    private final DocumentRepository documentRepository;
    private final DocumentRagRequestRepository requestRepository;
    private final DocumentStorageService documentStorageService;
    private final AiServiceClient aiServiceClient;
    private final DocumentRagStatusService documentRagStatusService;

    @Value("${app.ai-service.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl = "http://localhost:8080";

    @Override
    @Transactional
    public UUID createAddRequest(UUID advisorId, UUID documentId) {
        Account advisor = accountRepository.findById(advisorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account was not found"));

        if (advisor.getRole() != AccountRole.ADVISOR || advisor.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active advisor account is required");
        }

        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found"));

        if (!document.getUploadedBy().getId().equals(advisorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only submit requests for your own documents");
        }

        requireAddEligibleDocument(document);
        if (requestRepository.existsByDocumentAndStatus(document, DocumentRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document already has a pending request");
        }

        DocumentRagRequest request = new DocumentRagRequest(document, advisor);

        boolean hasExistingRequest = requestRepository
                .existsByDocumentAndRequestTypeAndStatusIn(
                        document,
                        DocumentRequestType.ADD,
                        List.of(DocumentRequestStatus.PENDING, DocumentRequestStatus.APPROVED)
                );

        if (hasExistingRequest) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document already has a pending or approved add request");
        }
        return savePendingRequest(request);
    }

    @Override
    @Transactional
    public UUID createUpdateRequest(
            UUID advisorId,
            UUID documentId,
            CreateDocumentUpdateRequest updateRequest
    ) {
        throw new ResponseStatusException(HttpStatus.GONE, "Edit document metadata before submitting it for approval");
    }

    @Override
    @Transactional
    public UUID createDeleteRequest(UUID advisorId, UUID documentId) {
        Account advisor = requireActiveAdvisor(advisorId);
        Document document = requireOwnedLockedDocument(advisorId, documentId);

        requireActiveDocument(document, "Only active documents are eligible for DELETE");

        if (requestRepository.existsByDocumentAndStatus(document, DocumentRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document already has a pending request");
        }

        return savePendingRequest(DocumentRagRequest.createDelete(document, advisor));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRagRequestResponse> getMyRequests(UUID advisorId) {
        Account advisor = accountRepository.findById(advisorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account was not found"));

        if (advisor.getRole() != AccountRole.ADVISOR || advisor.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active advisor account is required");
        }

        return requestRepository.findByRequestedByAdvisorOrderByCreatedAtDesc(advisor)
                .stream()
                .map(DocumentRagRequestResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRagRequestResponse> getPendingRequests() {
        return requestRepository.findByStatusOrderByCreatedAtDesc(DocumentRequestStatus.PENDING)
                .stream()
                .map(DocumentRagRequestResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void approveRequest(UUID adminId, UUID requestId) {
        Account admin = accountRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account was not found"));

        if (admin.getRole() != AccountRole.ADMIN || admin.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active admin account is required");
        }

        DocumentRagRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document request was not found"));

        if (request.getStatus() != DocumentRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending requests can be processed");
        }
        Document lockedDocument = documentRepository.findByIdForUpdate(request.getDocument().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found"));

        switch (request.getRequestType()) {
            case ADD -> {
                requireAddEligibleDocument(lockedDocument);
                request.approve(admin);
                lockedDocument.startRagProcessing();
                scheduleIngestion(lockedDocument);
            }
            case UPDATE -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Legacy UPDATE requests can only be rejected"
            );
            case DELETE -> {
                requireActiveDocument(lockedDocument, "Only active documents are eligible for DELETE");
                request.approve(admin);
                lockedDocument.archive();
            }
        }
    }

    @Override
    @Transactional
    public void rejectRequest(UUID adminId, UUID requestId, String reason) {
        Account admin = accountRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account was not found"));

        if (admin.getRole() != AccountRole.ADMIN || admin.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active admin account is required");
        }

        DocumentRagRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document request was not found"));

        if (request.getStatus() != DocumentRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be reviewed");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must not be blank");
        }
        // Lock Document in the same request -> document order as approveRequest.
        documentRepository.findByIdForUpdate(request.getDocument().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found"));

        request.reject(admin, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedDocumentFile getReviewFile(UUID adminId, UUID requestId) {
        Account admin = accountRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account was not found"));
        if (admin.getRole() != AccountRole.ADMIN || admin.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active admin account is required");
        }

        DocumentRagRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document request was not found"));
        requirePendingAddRequest(request);
        Document document = request.getDocument();
        requireAddEligibleDocument(document);

        try {
            return new DownloadedDocumentFile(
                    document.getOriginalFilename(),
                    document.getMimeType(),
                    documentStorageService.loadBytes(document.getStoragePath()),
                    document.isAllowDownload()
            );
        } catch (IOException exception) {
            throw new DocumentStorageException("Could not load document for review from RustFS", exception);
        }
    }

    private void requirePendingAddRequest(DocumentRagRequest request) {
        if (request.getStatus() != DocumentRequestStatus.PENDING
                || request.getRequestType() != DocumentRequestType.ADD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending ADD requests can be processed");
        }
    }

    private void requireAddEligibleDocument(Document document) {
        if (document == null
                || document.getLifecycleStatus() != DocumentLifecycleStatus.DRAFT
                || document.getRagStatus() != RagStatus.NOT_INDEXED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unprocessed draft documents are eligible for ADD");
        }
    }

    private void requireActiveDocument(Document document, String errorMessage) {
        if (!document.isEligibleForRag()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, errorMessage);
        }
    }

    private Account requireActiveAdvisor(UUID advisorId) {
        Account advisor = accountRepository.findById(advisorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account was not found"));

        if (advisor.getRole() != AccountRole.ADVISOR || advisor.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active advisor account is required");
        }
        return advisor;
    }

    private Document requireOwnedLockedDocument(UUID advisorId, UUID documentId) {
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found"));

        if (!document.getUploadedBy().getId().equals(advisorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only submit requests for your own documents");
        }
        return document;
    }

    private UUID savePendingRequest(DocumentRagRequest request) {
        try {
            DocumentRagRequest saved = requestRepository.save(request);
            requestRepository.flush();
            return saved.getId();
        } catch (DataIntegrityViolationException exception) {
            if (isOnePendingConstraintViolation(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This document already has a pending request",
                        exception
                );
            }
            throw exception;
        }
    }

    private boolean isOnePendingConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT)
                            .contains(ONE_PENDING_INDEX_NAME)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    @Transactional
    public void retryRag(UUID adminId, UUID documentId) {
        Account admin = accountRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account was not found"));
        if (admin.getRole() != AccountRole.ADMIN || admin.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active admin account is required");
        }
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found"));
        document.retryRagProcessing();
        scheduleIngestion(document);
    }

    @Override
    public void scheduleIngestion(Document document) {
        scheduleStorageIngestion(document.getId(), document.getStoragePath(), document.getOriginalFilename(),
                document.getTitle(), document.getDescription(), document.getRagAttemptId());
    }

    private void scheduleStorageIngestion(
            UUID documentId,
            String storagePath,
            String originalFilename,
            String title,
            String description,
            UUID attemptId
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    triggerStorageIngestion(documentId, storagePath, originalFilename, title, description, attemptId);
                }
            });
            return;
        }
        triggerStorageIngestion(documentId, storagePath, originalFilename, title, description, attemptId);
    }

    private void triggerStorageIngestion(
            UUID documentId,
            String storagePath,
            String originalFilename,
            String title,
            String description,
            UUID attemptId
    ) {
        if (aiServiceClient == null || documentId == null) {
            return;
        }

        String normalizedBaseUrl = callbackBaseUrl != null
                ? callbackBaseUrl.replaceAll("/+$", "")
                : "http://localhost:8080";
        String callbackUrl = normalizedBaseUrl + "/api/internal/documents/" + documentId + "/rag-status";
        if (attemptId != null) {
            callbackUrl += "?attemptId=" + attemptId;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (title != null && !title.isBlank()) {
            metadata.put("title", title.strip());
        }
        if (description != null && !description.isBlank()) {
            metadata.put("description", description.strip());
        }

        try {
            aiServiceClient.ingestFromStorage(new AiServiceIngestRequest(
                    documentId.toString(),
                    storagePath,
                    originalFilename,
                    callbackUrl,
                    metadata
            ));
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to trigger AI ingestion for document {}", documentId, exception);
            if (documentRagStatusService != null) {
                if (attemptId == null) {
                    documentRagStatusService.markFailed(documentId);
                } else {
                    documentRagStatusService.markFailed(documentId, attemptId);
                }
            }
        }
    }
}
