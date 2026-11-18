package vn.hcmute.edu.sia.service.implement;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.DocumentRagStatusService;

@Service
@RequiredArgsConstructor
public class DocumentRagStatusServiceImpl implements DocumentRagStatusService {

    private final DocumentRepository documentRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(UUID documentId) {
        Document document = findDocumentForUpdate(documentId);
        document.completeRagIndexing();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID documentId) {
        Document document = findDocumentForUpdate(documentId);
        document.failRagIndexing();
    }

    private Document findDocumentForUpdate(UUID documentId) {
        return documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Document was not found"
                ));
    }
}
