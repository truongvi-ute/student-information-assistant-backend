package vn.hcmute.edu.sia.service.implement;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import vn.hcmute.edu.sia.dto.internal.StoredDocumentFile;
import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
import vn.hcmute.edu.sia.dto.request.CreateDocumentUpdateRequest;
import vn.hcmute.edu.sia.dto.response.DocumentContentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentUploadResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.exception.DocumentStorageException;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.repository.DocumentRagRequestRepository;
import vn.hcmute.edu.sia.service.DocumentService;
import vn.hcmute.edu.sia.service.DocumentStorageService;
import vn.hcmute.edu.sia.validation.DocumentUploadValidator;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final AccountRepository accountRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentUploadValidator documentUploadValidator;
    private final DocumentRagRequestRepository requestRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public DocumentUploadResponse uploadDocument(
            UUID uploaderId,
            UploadDocumentRequest request
    ) {
        Account uploader = requireActiveAccount(uploaderId);

        if (uploader.getRole() != AccountRole.ADVISOR) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "An active advisor account is required to upload documents"
            );
        }

        String mimeType = documentUploadValidator.validateAndGetMimeType(
                request == null ? null : request.file()
        );

        StoredDocumentFile storedFile;
        try {
            storedFile = documentStorageService.store(request.file(), mimeType);
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "Could not store the document in RustFS",
                    exception
            );
        }

        try {
            boolean allowDownload = request.allowDownload() == null
                    || request.allowDownload();
            Document document = new Document(
                    uploader,
                    request.title(),
                    request.description(),
                    allowDownload,
                    storedFile.originalFilename(),
                    storedFile.storagePath(),
                    storedFile.mimeType(),
                    storedFile.sizeBytes(),
                    storedFile.checksumSha256()
            );

            Document savedDocument = documentRepository.saveAndFlush(document);
            return DocumentUploadResponse.from(savedDocument);
        } catch (RuntimeException exception) {
            deleteStoredFileAfterFailure(storedFile, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public void updateDraft(UUID requesterId, UUID documentId, CreateDocumentUpdateRequest request) {
        Document document = requireEditableDraft(requesterId, documentId, true);
        document.updateDraftMetadata(request.getTitle(), request.getDescription(), request.getAllowDownload());
    }

    @Override
    @Transactional
    public void deleteDraft(UUID advisorId, UUID documentId) {
        Document document = requireEditableDraft(advisorId, documentId, false);
        requestRepository.keepDeletedDocumentHistory(
                document, DocumentRequestStatus.REJECTED, documentId, document.getTitle()
        );
        jdbcTemplate.update("INSERT INTO document_storage_cleanup (storage_path) VALUES (?)", document.getStoragePath());
        documentRepository.delete(document);
    }

    private Document requireEditableDraft(UUID requesterId, UUID documentId, boolean allowAdmin) {
        Account requester = requireActiveAccount(requesterId);
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document was not found"));
        boolean ownsDraft = requester.getRole() == AccountRole.ADVISOR
                && document.getUploadedBy().getId().equals(requesterId);
        if (!ownsDraft && !(allowAdmin && requester.getRole() == AccountRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot modify this draft");
        }
        if (document.getLifecycleStatus() != DocumentLifecycleStatus.DRAFT
                || document.getRagStatus() != RagStatus.NOT_INDEXED
                || requestRepository.existsByDocumentAndStatus(document, DocumentRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This document can no longer be edited as a draft");
        }
        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(UUID requesterId) {
        Account requester = requireActiveAccount(requesterId);

        List<Document> documents = switch (requester.getRole()) {
            case ADMIN -> documentRepository.findAllWithUploaderOrderByCreatedAtDesc();
            case ADVISOR -> documentRepository.findVisibleForAdvisorOrderByCreatedAtDesc(requesterId);
            case STUDENT -> throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only advisors and admins can list documents"
            );
        };

        return documents.stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContentResponse getDocumentContent(UUID requesterId, UUID documentId) {
        Account requester = requireActiveAccount(requesterId);
        Document document = requireDocument(documentId);
        requireReadableDocument(requesterId, requester, document);

        String extractedKey = "extracted/" + document.getId() + ".md";
        String markdownContent;
        try {
            markdownContent = documentStorageService.loadUtf8Text(extractedKey);
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "Could not read extracted document content from RustFS",
                    exception
            );
        }

        return new DocumentContentResponse(
                document.getId(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.isDownloadable(),
                markdownContent
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedDocumentFile previewDocument(UUID requesterId, UUID documentId) {
        return loadOriginalDocument(requesterId, documentId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedDocumentFile downloadDocument(UUID requesterId, UUID documentId) {
        return loadOriginalDocument(requesterId, documentId, true);
    }

    private DownloadedDocumentFile loadOriginalDocument(
            UUID requesterId,
            UUID documentId,
            boolean requireDownloadable
    ) {
        Account requester = requireActiveAccount(requesterId);
        Document document = requireDocument(documentId);
        requireReadableDocument(requesterId, requester, document);

        if (requireDownloadable && !document.isDownloadable()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This document is not available for download"
            );
        }

        byte[] content;
        try {
            content = documentStorageService.loadBytes(document.getStoragePath());
        } catch (IOException exception) {
            throw new DocumentStorageException(
                    "Could not load the document from RustFS",
                    exception
            );
        }

        return new DownloadedDocumentFile(
                document.getOriginalFilename(),
                document.getMimeType(),
                content,
                document.isDownloadable()
        );
    }

    private void requireReadableDocument(UUID requesterId, Account requester, Document document) {
        if (document.getLifecycleStatus() == DocumentLifecycleStatus.ARCHIVED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Archived documents can no longer be accessed"
            );
        }

        if (requester.getRole() == AccountRole.STUDENT && !document.isEligibleForRag()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This document is not available for students"
            );
        }

        if (requester.getRole() == AccountRole.ADVISOR) {
            if (document.getLifecycleStatus() == DocumentLifecycleStatus.DRAFT
                    && (document.getRagStatus() == RagStatus.PROCESSING
                    || document.getRagStatus() == RagStatus.FAILED)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Documents in processing or failed state are status-only for advisors"
                );
            }

            if (document.getLifecycleStatus() != DocumentLifecycleStatus.ACTIVE
                    && (document.getUploadedBy() == null || !requesterId.equals(document.getUploadedBy().getId()))) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not have permission to access this document"
                );
            }
        }
    }

    private Account requireActiveAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Account was not found"
                ));

        if (account.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is not active"
            );
        }

        return account;
    }

    private Document requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document was not found"
                ));
    }

    private void deleteStoredFileAfterFailure(
            StoredDocumentFile storedFile,
            RuntimeException originalException
    ) {
        try {
            documentStorageService.delete(storedFile.storagePath());
        } catch (IOException | RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
