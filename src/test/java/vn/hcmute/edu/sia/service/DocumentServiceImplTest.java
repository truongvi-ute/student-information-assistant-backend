package vn.hcmute.edu.sia.service;

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
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.DocumentStorageService;
import vn.hcmute.edu.sia.service.implement.DocumentServiceImpl;
import vn.hcmute.edu.sia.validation.DocumentUploadValidator;

import java.io.IOException;
import java.lang.reflect.Field;
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

    @Test
    @DisplayName("Sinh viên lấy nội dung Markdown của tài liệu ACTIVE + READY thành công")
    void getDocumentContent_ActiveStudentEligibleDoc_ReturnsMarkdown() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Account student = new Account("Student", "s@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        Document doc = new Document(advisor, "Quy chế", null, true, "quy_che.pdf", "documents/qc.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(doc, docId);
        doc.completeRagIndexing();

        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(documentStorageService.loadUtf8Text("extracted/" + docId + ".md")).thenReturn("# Quy chế");

        DocumentContentResponse response = documentService.getDocumentContent(studentId, docId);

        assertThat(response.documentId()).isEqualTo(docId);
        assertThat(response.markdownContent()).isEqualTo("# Quy chế");
        assertThat(response.allowDownload()).isTrue();
    }

    @Test
    @DisplayName("Sinh viên bị chặn đọc tài liệu chưa READY/ACTIVE")
    void getDocumentContent_StudentDraftDoc_ThrowsForbidden() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Account student = new Account("Student", "s@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        Document doc = new Document(advisor, "Nháp", null, true, "draft.pdf", "documents/draft.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(doc, docId);

        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.getDocumentContent(studentId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Tải file gốc thành công khi allowDownload = true")
    void downloadDocument_EligibleAndDownloadable_ReturnsBytes() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Account student = new Account("Student", "s@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        Document doc = new Document(advisor, "Quy chế", null, true, "quy_che.pdf", "documents/qc.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(doc, docId);
        doc.completeRagIndexing();

        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(documentStorageService.loadBytes("documents/qc.pdf")).thenReturn("pdf-bytes".getBytes());

        DownloadedDocumentFile downloaded = documentService.downloadDocument(studentId, docId);

        assertThat(downloaded.originalFilename()).isEqualTo("quy_che.pdf");
        assertThat(downloaded.mimeType()).isEqualTo("application/pdf");
        assertThat(downloaded.content()).isEqualTo("pdf-bytes".getBytes());
    }

    @Test
    @DisplayName("Chặn tải file gốc khi allowDownload = false")
    void downloadDocument_AllowDownloadFalse_ThrowsForbidden() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Account student = new Account("Student", "s@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        Document doc = new Document(advisor, "Nội bộ", null, false, "no_dl.pdf", "documents/no_dl.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(doc, docId);
        doc.completeRagIndexing();

        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.downloadDocument(studentId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void setId(Document doc, UUID id) throws Exception {
        Field field = Document.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(doc, id);
    }
}
