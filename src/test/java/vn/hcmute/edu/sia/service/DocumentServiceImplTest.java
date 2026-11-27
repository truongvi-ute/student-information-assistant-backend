package vn.hcmute.edu.sia.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @Mock
    private DocumentRagRequestService ragRequestService;

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
    void adminUploadEntersProcessingAndSchedulesIngestion() throws IOException {
        var admin = new Account("Admin", "admin@test.edu.vn", "hash",
                AccountRole.ADMIN, AccountAccessStatus.ACTIVE, false);
        UUID adminId = UUID.randomUUID();
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(documentUploadValidator.validateAndGetMimeType(file)).thenReturn("application/pdf");
        when(documentStorageService.store(file, "application/pdf")).thenReturn(storedFile);
        when(documentRepository.saveAndFlush(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = documentService.uploadDocument(adminId, new UploadDocumentRequest(file, "Quy chế", null, true));

        assertThat(response.lifecycleStatus()).isEqualTo(DocumentLifecycleStatus.DRAFT);
        assertThat(response.ragStatus()).isEqualTo(RagStatus.PROCESSING);
        verify(ragRequestService).scheduleIngestion(any(Document.class));
    }

    @Test
    @DisplayName("Upload thất bại khi uploader là Student")
    void uploadDocument_Student_ThrowsForbidden() {
        Account student = new Account("Student", "student@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        UUID studentId = UUID.randomUUID();
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));

        UploadDocumentRequest request = new UploadDocumentRequest(file, "Quy chế", null, true);

        assertThatThrownBy(() -> documentService.uploadDocument(studentId, request))
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
        UUID adminId = UUID.randomUUID();
        Account admin = new Account("Admin", "admin@test.edu.vn", "hash", AccountRole.ADMIN, AccountAccessStatus.ACTIVE, false);
        Document doc = new Document(advisor, "Tài liệu 1", null, true, "t1.pdf", "p1", "application/pdf", 100L, "c".repeat(64));
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findAllWithUploaderOrderByCreatedAtDesc()).thenReturn(List.of(doc));

        List<DocumentResponse> result = documentService.getDocuments(adminId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().uploadedById()).isEqualTo(advisor.getId());
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
        assertThat(downloaded.allowDownload()).isTrue();
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

    @Test
    @DisplayName("Xem trước file gốc vẫn thành công ngay cả khi allowDownload = false")
    void previewDocument_AllowDownloadFalse_StillReturnsBytesForStudent() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Account student = new Account("Student", "s@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        Document doc = new Document(advisor, "Nội bộ", null, false, "view_only.pdf", "documents/view_only.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(doc, docId);
        doc.completeRagIndexing();

        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(documentStorageService.loadBytes("documents/view_only.pdf")).thenReturn("pdf-preview".getBytes());

        DownloadedDocumentFile previewed = documentService.previewDocument(studentId, docId);

        assertThat(previewed.originalFilename()).isEqualTo("view_only.pdf");
        assertThat(previewed.content()).isEqualTo("pdf-preview".getBytes());
        assertThat(previewed.allowDownload()).isFalse();
    }

    @Test
    @DisplayName("Advisor chỉ lấy được danh sách tài liệu trong phạm vi của mình hoặc ACTIVE")
    void getDocuments_Advisor_UsesScopedQuery() {
        Document doc = new Document(advisor, "Tài liệu riêng", null, true, "t2.pdf", "p2", "application/pdf", 100L, "c".repeat(64));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(documentRepository.findVisibleForAdvisorOrderByCreatedAtDesc(advisorId)).thenReturn(List.of(doc));

        List<DocumentResponse> result = documentService.getDocuments(advisorId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Tài liệu riêng");
        verify(documentRepository).findVisibleForAdvisorOrderByCreatedAtDesc(advisorId);
        verify(documentRepository, never()).findAllWithUploaderOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("Advisor khác bị chặn đọc content, preview và download tài liệu DRAFT của người khác")
    void accessOtherAdvisorDraft_ThrowsForbiddenAcrossAllEndpoints() throws Exception {
        setAccountId(advisor, advisorId);
        UUID otherAdvisorId = UUID.randomUUID();
        Account otherAdvisor = new Account("Other Advisor", "other@test.edu.vn", "hash", AccountRole.ADVISOR, AccountAccessStatus.ACTIVE, false);
        setAccountId(otherAdvisor, otherAdvisorId);
        UUID docId = UUID.randomUUID();
        Document draftDoc = new Document(advisor, "Nháp riêng", null, true, "draft.pdf", "documents/draft.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(draftDoc, docId);

        when(accountRepository.findById(otherAdvisorId)).thenReturn(Optional.of(otherAdvisor));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(draftDoc));

        assertThatThrownBy(() -> documentService.getDocumentContent(otherAdvisorId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> documentService.previewDocument(otherAdvisorId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> documentService.downloadDocument(otherAdvisorId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(documentStorageService);
    }

    @Test
    @DisplayName("Advisor chủ sở hữu vẫn xem trước được file DRAFT của chính mình")
    void ownerAdvisorCanPreviewOwnDraft() throws Exception {
        setAccountId(advisor, advisorId);
        UUID docId = UUID.randomUUID();
        Document draftDoc = new Document(advisor, "Nháp của tôi", null, true, "own.pdf", "documents/own.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(draftDoc, docId);

        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(draftDoc));
        when(documentStorageService.loadBytes("documents/own.pdf")).thenReturn("own-bytes".getBytes());

        DownloadedDocumentFile previewed = documentService.previewDocument(advisorId, docId);

        assertThat(previewed.content()).isEqualTo("own-bytes".getBytes());
    }

    @Test
    @DisplayName("Chặn cả content, preview và download khi tài liệu đã ARCHIVED")
    void archivedDocument_BlocksContentPreviewAndDownloadForAllRoles() throws Exception {
        setAccountId(advisor, advisorId);
        UUID studentId = UUID.randomUUID();
        Account student = new Account("Student", "s@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        setAccountId(student, studentId);
        UUID adminId = UUID.randomUUID();
        Account admin = new Account("Admin", "admin@test.edu.vn", "hash", AccountRole.ADMIN, AccountAccessStatus.ACTIVE, false);
        setAccountId(admin, adminId);

        UUID docId = UUID.randomUUID();
        Document archivedDoc = new Document(advisor, "Quy chế đã thu hồi", null, true, "old.pdf", "documents/old.pdf", "application/pdf", 100L, "c".repeat(64));
        setId(archivedDoc, docId);
        archivedDoc.completeRagIndexing();
        archivedDoc.archive();

        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(archivedDoc));

        for (UUID actorId : List.of(advisorId, studentId, adminId)) {
            assertThatThrownBy(() -> documentService.getDocumentContent(actorId, docId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> documentService.previewDocument(actorId, docId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> documentService.downloadDocument(actorId, docId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verifyNoInteractions(documentStorageService);
    }

    @ParameterizedTest
    @EnumSource(value = RagStatus.class, names = {"PROCESSING", "FAILED"})
    @DisplayName("Advisor chủ sở hữu dù allowDownload=true vẫn bị chặn 403 trên content, preview và download khi DRAFT PROCESSING/FAILED")
    void ownerAdvisor_BlocksContentPreviewAndDownloadWhenDraftProcessingOrFailed(RagStatus ragStatus) throws Exception {
        setAccountId(advisor, advisorId);
        UUID docId = UUID.randomUUID();
        Document doc = new Document(advisor, "Tài liệu xử lý", null, true, "proc.pdf", "documents/proc.pdf", "application/pdf", 100L, "d".repeat(64));
        setId(doc, docId);
        doc.startRagProcessing();
        if (ragStatus == RagStatus.FAILED) {
            doc.failRagIndexing();
        }

        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.getDocumentContent(advisorId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> documentService.previewDocument(advisorId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> documentService.downloadDocument(advisorId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(documentStorageService);
    }

    @ParameterizedTest
    @EnumSource(value = RagStatus.class, names = {"PROCESSING", "FAILED"})
    @DisplayName("Admin vẫn preview được tài liệu DRAFT PROCESSING hoặc FAILED")
    void adminCanStillPreviewProcessingOrFailedDraft(RagStatus ragStatus) throws Exception {
        UUID adminId = UUID.randomUUID();
        Account admin = new Account("Admin", "admin@test.edu.vn", "hash", AccountRole.ADMIN, AccountAccessStatus.ACTIVE, false);
        setAccountId(admin, adminId);
        UUID docId = UUID.randomUUID();
        Document doc = new Document(advisor, "Tài liệu Admin kiểm tra", null, false, "proc.pdf", "documents/proc.pdf", "application/pdf", 100L, "d".repeat(64));
        setId(doc, docId);
        doc.startRagProcessing();
        if (ragStatus == RagStatus.FAILED) {
            doc.failRagIndexing();
        }

        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(documentStorageService.loadBytes("documents/proc.pdf")).thenReturn("admin-preview".getBytes());

        DownloadedDocumentFile previewed = documentService.previewDocument(adminId, docId);

        assertThat(previewed.content()).isEqualTo("admin-preview".getBytes());
    }

    @Test
    @DisplayName("Advisor vẫn xem được ACTIVE/READY và chỉ tải được khi allowDownload=true")
    void advisorCanReadActiveReadyAndDownloadOnlyWhenAllowed() throws Exception {
        setAccountId(advisor, advisorId);
        UUID docId = UUID.randomUUID();
        Document restrictedActiveDoc = new Document(advisor, "Active không cho tải", null, false, "active.pdf", "documents/active.pdf", "application/pdf", 100L, "e".repeat(64));
        setId(restrictedActiveDoc, docId);
        restrictedActiveDoc.completeRagIndexing();

        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(restrictedActiveDoc));
        when(documentStorageService.loadBytes("documents/active.pdf")).thenReturn("active-pdf".getBytes());
        when(documentStorageService.loadUtf8Text("extracted/" + docId + ".md")).thenReturn("# Active");

        assertThat(documentService.getDocumentContent(advisorId, docId).markdownContent()).isEqualTo("# Active");
        assertThat(documentService.previewDocument(advisorId, docId).content()).isEqualTo("active-pdf".getBytes());
        assertThatThrownBy(() -> documentService.downloadDocument(advisorId, docId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void setAccountId(Account account, UUID id) throws Exception {
        Field field = Account.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(account, id);
    }

    private static void setId(Document doc, UUID id) throws Exception {
        Field field = Document.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(doc, id);
    }
}
