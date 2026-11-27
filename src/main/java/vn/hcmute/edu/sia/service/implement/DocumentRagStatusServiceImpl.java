package vn.hcmute.edu.sia.service.implement;

import java.util.UUID;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.DocumentRagStatusService;

@Service
@RequiredArgsConstructor
public class DocumentRagStatusServiceImpl implements DocumentRagStatusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRagStatusServiceImpl.class);

    private final DocumentRepository documentRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(UUID documentId) {
        markReady(documentId, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(UUID documentId, UUID attemptId) {
        Document document = findDocumentForUpdate(documentId);
        if (!isCurrentAttempt(document, attemptId)) {
            return;
        }
        document.completeRagIndexing();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID documentId) {
        markFailed(documentId, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID documentId, UUID attemptId) {
        Document document = findDocumentForUpdate(documentId);
        if (!isCurrentAttempt(document, attemptId)) {
            return;
        }
        document.failRagIndexing();
    }

    private boolean isCurrentAttempt(Document document, UUID attemptId) {
        if (document.getLifecycleStatus() != DocumentLifecycleStatus.DRAFT
                || document.getRagStatus() != RagStatus.PROCESSING
                || !Objects.equals(document.getRagAttemptId(), attemptId)) {
            LOGGER.debug("Ignoring callback outside the current RAG attempt for document {}", document.getId());
            return false;
        }
        return true;
    }

    private Document findDocumentForUpdate(UUID documentId) {
        return documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document was not found"
                ));
    }
}
