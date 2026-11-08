package vn.hcmute.edu.sia.service.implement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.StoredDocumentFile;
import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
import vn.hcmute.edu.sia.dto.response.DocumentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentUploadResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.DocumentStorageService;
import vn.hcmute.edu.sia.validation.DocumentUploadValidator;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private DocumentUploadValidator documentUploadValidator;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private Account advisor;
    private UUID advisorId;
    private MockMultipartFile file;
    private StoredDocumentFile storedFile;

    @BeforeEach
    void setUp() {
        advisorId = UUID.randomUUID();
        advisor = new Account("Advisor Test", "advisor@test.edu.vn", "hash", AccountRole.ADVISOR, AccountAccessStatus.ACTIVE, false);

        file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        storedFile = new StoredDocumentFile("documents/test.pdf", "test.pdf", "application/pdf", 100L, "a".repeat(64));
    }

    @Test
    @DisplayName("Upload tài liệu thành công với Advisor")
    void uploadDocument_Advisor_Success() throws IOException {
        UploadDocumentRequest request = new UploadDocumentRequest(file, "Quy chế đào tạo", "Mô tả", true);

        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(documentUploadValidator.validateAndGetMimeType(file)).thenReturn("application/pdf");
        when(documentStorageService.store(file, "application/pdf")).thenReturn(storedFile);
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentUploadResponse response = documentService.uploadDocument(advisorId, request);

        assertThat(response).isNotNull();
        assertThat(response.title()).isEqualTo("Quy chế đào tạo");
        assertThat(response.ragStatus()).isEqualTo(RagStatus.NOT_INDEXED);
        assertThat(response.lifecycleStatus()).isEqualTo(DocumentLifecycleStatus.DRAFT);
        verify(documentRepository).saveAndFlush(any(Document.class));
    }

    @Test
    @DisplayName("Upload thất bại khi uploader là Admin")
    void uploadDocument_Admin_ThrowsForbidden() {
        Account admin = new Account("Admin", "admin@test.edu.vn", "hash", AccountRole.ADMIN, AccountAccessStatus.ACTIVE, false);
        UUID adminId = UUID.randomUUID();
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));

        UploadDocumentRequest request = new UploadDocumentRequest(file, "Quy chế", null, true);

        assertThatThrownBy(() -> documentService.uploadDocument(adminId, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Upload thất bại khi tài khoản bị khóa")
    void uploadDocument_BlockedAccount_ThrowsForbidden() {
        Account blockedAdvisor = new Account("Blocked", "b@test.edu.vn", "hash", AccountRole.ADVISOR, AccountAccessStatus.BLOCKED, false);
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(blockedAdvisor));

        UploadDocumentRequest request = new UploadDocumentRequest(file, "Quy chế", null, true);

        assertThatThrownBy(() -> documentService.uploadDocument(advisorId, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Lấy danh sách tài liệu thành công")
    void getDocuments_Success() {
        Document doc = new Document(advisor, "Tài liệu 1", null, true, "t1.pdf", "p1", "application/pdf", 100L, "c".repeat(64));
        when(documentRepository.findAllWithUploaderOrderByCreatedAtDesc()).thenReturn(List.of(doc));

        List<DocumentResponse> result = documentService.getDocuments();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Tài liệu 1");
    }
}
