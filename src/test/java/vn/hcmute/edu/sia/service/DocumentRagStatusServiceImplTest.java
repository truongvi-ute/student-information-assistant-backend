package vn.hcmute.edu.sia.service;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.implement.DocumentRagStatusServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentRagStatusServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentRagStatusServiceImpl service;

    private UUID documentId;
    private Document document;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        Account advisor = new Account(
                "Advisor",
                "advisor@test.edu.vn",
                "hash",
                AccountRole.ADVISOR,
                AccountAccessStatus.ACTIVE,
                false
        );
        document = new Document(
                advisor,
                "Quy chế",
                "Mô tả",
                true,
                "qc.pdf",
                "documents/qc.pdf",
                "application/pdf",
                1024L,
                "a".repeat(64)
        );
    }

    @Test
    void markReadyAndMarkFailedIgnoreArchivedDocument() {
        document.completeRagIndexing();
        document.archive();
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));

        service.markReady(documentId);
        assertThat(document.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ARCHIVED);
        assertThat(document.getRagStatus()).isEqualTo(RagStatus.DISABLED);

        service.markFailed(documentId);
        assertThat(document.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ARCHIVED);
        assertThat(document.getRagStatus()).isEqualTo(RagStatus.DISABLED);
    }

    @Test
    void markReadyTransitionsProcessingDocumentToActiveAndReady() {
        document.startRagProcessing();
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));

        service.markReady(documentId);

        assertThat(document.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ACTIVE);
        assertThat(document.getRagStatus()).isEqualTo(RagStatus.READY);
    }

    @Test
    void retryIgnoresCallbacksFromThePreviousAttempt() {
        document.startRagProcessing();
        document.failRagIndexing();
        UUID attempt = document.retryRagProcessing();
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));

        service.markReady(documentId);
        service.markFailed(documentId, UUID.randomUUID());
        assertThat(document.getRagStatus()).isEqualTo(RagStatus.PROCESSING);

        service.markReady(documentId, attempt);
        service.markFailed(documentId, attempt);
        assertThat(document.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ACTIVE);
        assertThat(document.getRagStatus()).isEqualTo(RagStatus.READY);
    }
}
