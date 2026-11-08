package vn.hcmute.edu.sia.service.implement;

import java.io.IOException;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import vn.hcmute.edu.sia.dto.internal.StoredDocumentFile;
import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
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
    public DocumentUploadResponse uploadDocument(
            UUID advisorId,
            UploadDocumentRequest request
    ) {
        Account advisor = accountRepository.findById(advisorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Advisor account was not found"
                ));

        if (advisor.getRole() != AccountRole.ADVISOR
                || advisor.getAccessStatus() != AccountAccessStatus.ACTIVE) {
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
                    advisor,
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
