package vn.hcmute.edu.sia.service.implement;

import java.util.LinkedHashMap;
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

@Service
@RequiredArgsConstructor
public class DocumentRagRequestServiceImpl implements DocumentRagRequestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRagRequestServiceImpl.class);

    private final AccountRepository accountRepository;
    private final DocumentRepository documentRepository;
    private final DocumentRagRequestRepository requestRepository;
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
        return requestRepository.save(request).getId();
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

        request.approve(admin);

        if (request.getRequestType() == DocumentRequestType.ADD) {
            Document document = request.getDocument();
            document.startRagProcessing();
            scheduleStorageIngestion(
                    document.getId(),
                    document.getStoragePath(),
                    document.getOriginalFilename(),
                    document.getTitle(),
                    document.getDescription()
            );
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

        request.reject(admin, reason);
    }

    private void scheduleStorageIngestion(
            UUID documentId,
            String storagePath,
            String originalFilename,
            String title,
            String description
    ) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    triggerStorageIngestion(documentId, storagePath, originalFilename, title, description);
                }
            });
            return;
        }
        triggerStorageIngestion(documentId, storagePath, originalFilename, title, description);
    }

    private void triggerStorageIngestion(
            UUID documentId,
            String storagePath,
            String originalFilename,
            String title,
            String description
    ) {
        if (aiServiceClient == null || documentId == null) {
            return;
        }

        String normalizedBaseUrl = callbackBaseUrl != null
                ? callbackBaseUrl.replaceAll("/+$", "")
                : "http://localhost:8080";
        String callbackUrl = normalizedBaseUrl + "/api/internal/documents/" + documentId + "/rag-status";
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
                documentRagStatusService.markFailed(documentId);
            }
        }
    }
}
