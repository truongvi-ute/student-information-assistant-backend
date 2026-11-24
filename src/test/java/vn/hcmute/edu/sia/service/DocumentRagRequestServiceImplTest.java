package vn.hcmute.edu.sia.service;

import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.exception.DocumentStorageException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.entity.DocumentRagRequest;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;
import vn.hcmute.edu.sia.exception.AiServiceException;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DocumentRagRequestRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.AiServiceClient;
import vn.hcmute.edu.sia.service.DocumentRagStatusService;
import vn.hcmute.edu.sia.service.implement.DocumentRagRequestServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentRagRequestServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentRagRequestRepository requestRepository;
    @Mock private DocumentStorageService documentStorageService;
    @Mock private AiServiceClient aiServiceClient;
    @Mock private DocumentRagStatusService documentRagStatusService;
    @Mock private Account advisor;
    @Mock private Account admin;
    @Mock private Document document;
    @InjectMocks private DocumentRagRequestServiceImpl service;

    private UUID advisorId;
    private UUID adminId;
    private UUID documentId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        advisorId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        requestId = UUID.randomUUID();
    }

    private void prepareAdvisorDocument() {
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getAccessStatus()).thenReturn(AccountAccessStatus.ACTIVE);
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));
        when(document.getUploadedBy()).thenReturn(advisor);
        when(advisor.getId()).thenReturn(advisorId);
    }

    private void prepareAdmin() {
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(admin.getRole()).thenReturn(AccountRole.ADMIN);
        when(admin.getAccessStatus()).thenReturn(AccountAccessStatus.ACTIVE);
    }

    @Test
    void createRequestSavesPendingAddWhenNoBlockingRequestExists() {
        prepareAdvisorDocument();
        prepareEligibleDocument();
        DocumentRagRequest saved = mock(DocumentRagRequest.class);
        when(saved.getId()).thenReturn(requestId);
        when(requestRepository.save(any(DocumentRagRequest.class))).thenReturn(saved);

        assertThat(service.createAddRequest(advisorId, documentId)).isEqualTo(requestId);

        ArgumentCaptor<DocumentRagRequest> captor = ArgumentCaptor.forClass(DocumentRagRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
        assertThat(captor.getValue().getRequestType()).isEqualTo(DocumentRequestType.ADD);
        assertThat(captor.getValue().getDocument()).isSameAs(document);
        assertThat(captor.getValue().getRequestedByAdvisor()).isSameAs(advisor);
        verify(requestRepository).existsByDocumentAndRequestTypeAndStatusIn(document, DocumentRequestType.ADD,
                List.of(DocumentRequestStatus.PENDING, DocumentRequestStatus.APPROVED));
    }

    @Test
    void createRequestRejectsExistingPendingOrApprovedRequest() {
        prepareAdvisorDocument();
        prepareEligibleDocument();
        when(requestRepository.existsByDocumentAndRequestTypeAndStatusIn(document, DocumentRequestType.ADD,
                List.of(DocumentRequestStatus.PENDING, DocumentRequestStatus.APPROVED))).thenReturn(true);

        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequestRejectsMissingAccount() {
        when(accountRepository.findById(advisorId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequestRejectsWrongRole() {
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(advisor.getRole()).thenReturn(AccountRole.STUDENT);
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequestRejectsBlockedAdvisor() {
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getAccessStatus()).thenReturn(AccountAccessStatus.BLOCKED);
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequestRejectsMissingDocument() {
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getAccessStatus()).thenReturn(AccountAccessStatus.ACTIVE);
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRequestRejectsAnotherAdvisorsDocument() {
        prepareAdvisorDocument();
        when(advisor.getId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void advisorSeesOnlyTheirRequests() {
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getAccessStatus()).thenReturn(AccountAccessStatus.ACTIVE);
        when(requestRepository.findByRequestedByAdvisorOrderByCreatedAtDesc(advisor))
                .thenReturn(List.of(new DocumentRagRequest(document, advisor)));
        when(document.getTitle()).thenReturn("Document");
        when(advisor.getFullName()).thenReturn("Advisor");

        var results = service.getMyRequests(advisorId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentTitle()).isEqualTo("Document");
        assertThat(results.get(0).requestedByName()).isEqualTo("Advisor");
    }

    @Test
    void pendingListUsesPendingStatus() {
        when(requestRepository.findByStatusOrderByCreatedAtDesc(DocumentRequestStatus.PENDING))
                .thenReturn(List.of(new DocumentRagRequest(document, advisor)));
        assertThat(service.getPendingRequests()).hasSize(1);
        verify(requestRepository).findByStatusOrderByCreatedAtDesc(DocumentRequestStatus.PENDING);
    }

    @Test
    void adminApprovesPendingRequest() {
        prepareAdmin();
        prepareEligibleDocument();
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        when(document.getStoragePath()).thenReturn("documents/" + documentId + ".pdf");
        when(document.getOriginalFilename()).thenReturn("quy_che.pdf");
        when(document.getTitle()).thenReturn("Quy chế");
        when(document.getDescription()).thenReturn("Quy chế học vụ trình độ đại học 2026");
        when(aiServiceClient.ingestFromStorage(any(AiServiceIngestRequest.class)))
                .thenReturn(new AiServiceIngestResponse("processing", documentId.toString(), "Accepted"));

        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        service.approveRequest(adminId, requestId);

        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.APPROVED);
        assertThat(request.getReviewedByAdmin()).isSameAs(admin);
        verify(document).startRagProcessing();

        ArgumentCaptor<AiServiceIngestRequest> ingestCaptor = ArgumentCaptor.forClass(AiServiceIngestRequest.class);
        verify(aiServiceClient).ingestFromStorage(ingestCaptor.capture());
        assertThat(ingestCaptor.getValue().metadata())
                .containsEntry("title", "Quy chế")
                .containsEntry("description", "Quy chế học vụ trình độ đại học 2026");
        verify(documentRagStatusService, never()).markFailed(any());
    }

    @Test
    void adminApprovesPendingRequestOmitsBlankDescriptionFromMetadata() {
        prepareAdmin();
        prepareEligibleDocument();
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        when(document.getStoragePath()).thenReturn("documents/" + documentId + ".pdf");
        when(document.getOriginalFilename()).thenReturn("quy_che.pdf");
        when(document.getTitle()).thenReturn("Quy chế");
        when(document.getDescription()).thenReturn("   ");
        when(aiServiceClient.ingestFromStorage(any(AiServiceIngestRequest.class)))
                .thenReturn(new AiServiceIngestResponse("processing", documentId.toString(), "Accepted"));

        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        service.approveRequest(adminId, requestId);

        ArgumentCaptor<AiServiceIngestRequest> ingestCaptor = ArgumentCaptor.forClass(AiServiceIngestRequest.class);
        verify(aiServiceClient).ingestFromStorage(ingestCaptor.capture());
        assertThat(ingestCaptor.getValue().metadata())
                .containsOnlyKeys("title")
                .containsEntry("title", "Quy chế");
    }

    @Test
    void adminApprovesPendingRequestMarksFailedWhenAiServiceFails() {
        prepareAdmin();
        prepareEligibleDocument();
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));
        when(document.getId()).thenReturn(documentId);
        when(document.getStoragePath()).thenReturn("documents/" + documentId + ".pdf");
        when(document.getOriginalFilename()).thenReturn("quy_che.pdf");
        when(document.getTitle()).thenReturn("Quy chế");
        when(aiServiceClient.ingestFromStorage(any(AiServiceIngestRequest.class)))
                .thenThrow(new AiServiceException(HttpStatus.BAD_GATEWAY, "AI service down"));

        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        service.approveRequest(adminId, requestId);

        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.APPROVED);
        verify(document).startRagProcessing();
        verify(documentRagStatusService).markFailed(documentId);
    }

    @Test
    void adminRejectsPendingRequest() {
        prepareAdmin();
        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        service.rejectRequest(adminId, requestId, "Incomplete");
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Incomplete");
    }

    @Test
    void reviewRejectsMissingAdmin() {
        when(accountRepository.findById(adminId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approveRequest(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(requestRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void reviewRejectsWrongRole() {
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(admin.getRole()).thenReturn(AccountRole.ADVISOR);
        assertThatThrownBy(() -> service.approveRequest(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(requestRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void reviewRejectsBlockedAdmin() {
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(admin.getRole()).thenReturn(AccountRole.ADMIN);
        when(admin.getAccessStatus()).thenReturn(AccountAccessStatus.BLOCKED);
        assertThatThrownBy(() -> service.rejectRequest(adminId, requestId, "Reason"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(requestRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void reviewRejectsMissingRequest() {
        prepareAdmin();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.approveRequest(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reviewRejectsAlreadyProcessedRequest() {
        prepareAdmin();
        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        request.approve(admin);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.rejectRequest(adminId, requestId, "Reason"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.APPROVED);
    }

    private void prepareEligibleDocument() {
        when(document.getLifecycleStatus()).thenReturn(DocumentLifecycleStatus.DRAFT);
        when(document.getRagStatus()).thenReturn(RagStatus.NOT_INDEXED);
    }

    private DocumentRagRequest prepareReviewFile() {
        prepareAdmin();
        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        return request;
    }

    @ParameterizedTest
    @EnumSource(value = DocumentLifecycleStatus.class, names = {"ACTIVE", "ARCHIVED"})
    void createRejectsNonDraftDocument(DocumentLifecycleStatus status) {
        prepareAdvisorDocument();
        when(document.getLifecycleStatus()).thenReturn(status);
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(requestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = RagStatus.class, names = {"PROCESSING", "READY", "FAILED", "DISABLED"})
    void createRejectsProcessedDraft(RagStatus status) {
        prepareAdvisorDocument();
        when(document.getLifecycleStatus()).thenReturn(DocumentLifecycleStatus.DRAFT);
        when(document.getRagStatus()).thenReturn(status);
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void createRejectsAnyPendingRequest() {
        prepareAdvisorDocument();
        prepareEligibleDocument();
        when(requestRepository.existsByDocumentAndStatus(document, DocumentRequestStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.createAddRequest(advisorId, documentId))
                .isInstanceOf(ResponseStatusException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void approvalRejectsChangedDocumentWithoutApprovingOrCallingAi() {
        prepareAdmin();
        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(document.getId()).thenReturn(documentId);
        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(document));
        when(document.getLifecycleStatus()).thenReturn(DocumentLifecycleStatus.ARCHIVED);
        assertThatThrownBy(() -> service.approveRequest(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void adminCanInspectFileWithoutStudentDownloadPermission() throws IOException {
        DocumentRagRequest request = prepareReviewFile();
        prepareEligibleDocument();
        when(document.getStoragePath()).thenReturn("documents/review.docx");
        when(document.getOriginalFilename()).thenReturn("review.docx");
        when(document.getMimeType()).thenReturn("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        when(documentStorageService.loadBytes("documents/review.docx")).thenReturn(new byte[]{1, 2});
        var result = service.getReviewFile(adminId, requestId);
        assertThat(result.content()).containsExactly((byte) 1, (byte) 2);
        assertThat(result.originalFilename()).isEqualTo("review.docx");
        assertThat(result.allowDownload()).isFalse();
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
        verify(document, never()).startRagProcessing();
        verifyNoInteractions(aiServiceClient);
    }

    @ParameterizedTest
    @EnumSource(value = AccountRole.class, names = {"STUDENT", "ADVISOR"})
    void reviewFileRejectsNonAdmin(AccountRole role) {
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(admin.getRole()).thenReturn(role);
        assertThatThrownBy(() -> service.getReviewFile(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(documentStorageService, requestRepository);
    }

    @Test
    void reviewFileRejectsBlockedAdmin() {
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(admin.getRole()).thenReturn(AccountRole.ADMIN);
        when(admin.getAccessStatus()).thenReturn(AccountAccessStatus.BLOCKED);
        assertThatThrownBy(() -> service.getReviewFile(adminId, requestId)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(documentStorageService);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentRequestStatus.class, names = {"APPROVED", "REJECTED"})
    void reviewFileRejectsProcessedRequest(DocumentRequestStatus status) {
        DocumentRagRequest request = prepareReviewFile();
        if (status == DocumentRequestStatus.APPROVED) request.approve(admin);
        else request.reject(admin, "Invalid content");
        assertThatThrownBy(() -> service.getReviewFile(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(documentStorageService);
    }

    @Test
    void reviewFileReportsStorageFailureWithoutChangingRequest() throws IOException {
        DocumentRagRequest request = prepareReviewFile();
        prepareEligibleDocument();
        when(document.getStoragePath()).thenReturn("documents/review.pdf");
        when(documentStorageService.loadBytes("documents/review.pdf")).thenThrow(new IOException("Storage down"));
        assertThatThrownBy(() -> service.getReviewFile(adminId, requestId)).isInstanceOf(DocumentStorageException.class);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
        verifyNoInteractions(aiServiceClient);
    }

    @Test
    void reviewFileRejectsMissingRequest() {
        prepareAdmin();
        assertThatThrownBy(() -> service.getReviewFile(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(documentStorageService);
    }

    @Test
    void reviewFileRejectsArchivedDocument() {
        prepareReviewFile();
        when(document.getLifecycleStatus()).thenReturn(DocumentLifecycleStatus.ARCHIVED);
        assertThatThrownBy(() -> service.getReviewFile(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(documentStorageService);
    }

    @Test
    void approvalRejectsAlreadyApprovedRequestBeforeCallingAi() {
        prepareAdmin();
        DocumentRagRequest request = new DocumentRagRequest(document, advisor);
        request.approve(admin);
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.approveRequest(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(aiServiceClient, documentRepository);
    }
}
