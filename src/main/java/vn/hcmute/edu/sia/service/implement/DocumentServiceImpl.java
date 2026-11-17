package vn.hcmute.edu.sia.service.implement;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import vn.hcmute.edu.sia.dto.internal.StoredDocumentFile;
import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
import vn.hcmute.edu.sia.dto.response.DocumentContentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentUploadResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.exception.DocumentStorageException;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
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
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments() {
        return documentRepository.findAllWithUploaderOrderByCreatedAtDesc()
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContentResponse getDocumentContent(UUID requesterId, UUID documentId) {
        Account requester = requireActiveAccount(requesterId);
        Document document = requireDocument(documentId);

        if (requester.getRole() == AccountRole.STUDENT && !document.isEligibleForRag()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This document is not available for students"
            );
        }

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
    public DownloadedDocumentFile downloadDocument(UUID requesterId, UUID documentId) {
        Account requester = requireActiveAccount(requesterId);
        Document document = requireDocument(documentId);

        if (requester.getRole() == AccountRole.STUDENT && !document.isEligibleForRag()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This document is not available for students"
            );
        }

        if (!document.isDownloadable()) {
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
                    "Could not download the document from RustFS",
                    exception
            );
        }

        return new DownloadedDocumentFile(
                document.getOriginalFilename(),
                document.getMimeType(),
                content
        );
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
