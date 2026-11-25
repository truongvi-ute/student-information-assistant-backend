package vn.hcmute.edu.sia.service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestResponse;
import vn.hcmute.edu.sia.dto.request.CreateDocumentUpdateRequest;
import vn.hcmute.edu.sia.dto.response.DocumentRagRequestResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;
import vn.hcmute.edu.sia.enums.RagStatus;
import vn.hcmute.edu.sia.exception.GlobalExceptionHandler;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DocumentRagRequestRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(PostgresTestSchemaExtension.class)
@SpringBootTest
class DocumentRagRequestPostgresIntegrationTest {

    private static final String ONE_PENDING_INDEX_NAME = "uq_document_rag_requests_one_pending";

    @DynamicPropertySource
    static void registerPostgresTestSchema(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestSchemaExtension::getSchemaUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", PostgresTestSchemaExtension::getUsername);
        registry.add("spring.datasource.password", PostgresTestSchemaExtension::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private DataSource dataSource;
    @Autowired private AccountRepository accountRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentRagRequestRepository requestRepository;
    @Autowired private DocumentRagRequestService requestService;
    @Autowired private DocumentService documentService;
    @Autowired private DocumentRagStatusService ragStatusService;

    @MockitoBean private DocumentStorageService documentStorageService;
    @MockitoBean private AiServiceClient aiServiceClient;

    private Account advisor;
    private Account otherAdvisor;
    private Account admin;
    private Account student;

    @BeforeEach
    void setUp() {
        assertIsolatedTestSchema();

        requestRepository.deleteAll();
        documentRepository.deleteAll();
        accountRepository.deleteAll();

        advisor = accountRepository.save(new Account(
                "Advisor Owner",
                "owner@hcmute.edu.vn",
                "hash",
                AccountRole.ADVISOR,
                AccountAccessStatus.ACTIVE,
                false
        ));
        otherAdvisor = accountRepository.save(new Account(
                "Other Advisor",
                "other@hcmute.edu.vn",
                "hash",
                AccountRole.ADVISOR,
                AccountAccessStatus.ACTIVE,
                false
        ));
        admin = accountRepository.save(new Account(
                "Admin Reviewer",
                "admin@hcmute.edu.vn",
                "hash",
                AccountRole.ADMIN,
                AccountAccessStatus.ACTIVE,
                false
        ));
        student = accountRepository.save(new Account(
                "Student Reader",
                "student@hcmute.edu.vn",
                "hash",
                AccountRole.STUDENT,
                AccountAccessStatus.ACTIVE,
                false
        ));
    }

    @Test
    void urlRewritingOverridesExistingCurrentSchemaParameter() {
        String testSchema = "sia_test_it_12345678";

        String noParams = "jdbc:postgresql://localhost:5433/sia_db";
        assertThat(PostgresTestSchemaExtension.stripSchemaParam(noParams)).isEqualTo(noParams);
        assertThat(PostgresTestSchemaExtension.withSchemaParam(noParams, testSchema))
                .isEqualTo("jdbc:postgresql://localhost:5433/sia_db?currentSchema=" + testSchema);

        String onlyPublic = "jdbc:postgresql://localhost:5433/sia_db?currentSchema=public";
        assertThat(PostgresTestSchemaExtension.stripSchemaParam(onlyPublic)).isEqualTo(noParams);
        assertThat(PostgresTestSchemaExtension.withSchemaParam(onlyPublic, testSchema))
                .isEqualTo("jdbc:postgresql://localhost:5433/sia_db?currentSchema=" + testSchema)
                .doesNotContain("currentSchema=public");

        String publicFirst = "jdbc:postgresql://localhost:5433/sia_db?currentSchema=public&sslmode=disable";
        assertThat(PostgresTestSchemaExtension.stripSchemaParam(publicFirst))
                .isEqualTo("jdbc:postgresql://localhost:5433/sia_db?sslmode=disable");
        assertThat(PostgresTestSchemaExtension.withSchemaParam(publicFirst, testSchema))
                .isEqualTo("jdbc:postgresql://localhost:5433/sia_db?sslmode=disable&currentSchema=" + testSchema)
                .doesNotContain("currentSchema=public");

        String publicLast = "jdbc:postgresql://localhost:5433/sia_db?sslmode=disable&currentSchema=public";
        assertThat(PostgresTestSchemaExtension.stripSchemaParam(publicLast))
                .isEqualTo("jdbc:postgresql://localhost:5433/sia_db?sslmode=disable");
        assertThat(PostgresTestSchemaExtension.withSchemaParam(publicLast, testSchema))
                .isEqualTo("jdbc:postgresql://localhost:5433/sia_db?sslmode=disable&currentSchema=" + testSchema)
                .doesNotContain("currentSchema=public");
    }

    @Test
    void partialUniqueIndexFromSchemaSqlEnforcesSinglePendingAndAllowsHistory() throws Exception {
        String indexDef = null;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() "
                             + "AND tablename = 'document_rag_requests' "
                             + "AND indexname = '" + ONE_PENDING_INDEX_NAME + "'")) {
            if (rs.next()) {
                indexDef = rs.getString(1);
            }
        }

        assertThat(indexDef).isNotNull();
        String lower = indexDef.toLowerCase(Locale.ROOT);
        assertThat(lower)
                .contains("create unique index")
                .contains("(document_id)")
                .contains("where")
                .contains("status");
        assertThat(indexDef)
                .contains("'PENDING'")
                .doesNotContain("<>")
                .doesNotContain("!=");

        Document doc = createActiveDocument("Kiểm tra ràng buộc", "Mô tả", true);
        UUID rejectedId = requestService.createDeleteRequest(advisor.getId(), doc.getId());
        requestService.rejectRequest(admin.getId(), rejectedId, "Chưa thu hồi");

        UUID secondPendingId = requestService.createDeleteRequest(advisor.getId(), doc.getId());
        assertThat(secondPendingId).isNotNull();

        assertThatThrownBy(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        "INSERT INTO document_rag_requests "
                                + "(document_rag_request_id, document_id, requested_by_advisor_id, request_type, status) "
                                + "VALUES ('" + UUID.randomUUID() + "', '" + doc.getId() + "', '" + advisor.getId() + "', 'UPDATE', 'PENDING')"
                );
            }
        }).hasMessageContaining(ONE_PENDING_INDEX_NAME);
    }

    @Test
    void updateWorkflowPreservesDocumentUntilApprovedAndClearsDescriptionOnApproval() {
        Document doc = createActiveDocument("Tên ban đầu", "Mô tả ban đầu", true);

        UUID req1Id = requestService.createUpdateRequest(
                advisor.getId(),
                doc.getId(),
                new CreateDocumentUpdateRequest("Tên mới bị từ chối", null, false)
        );

        Document whilePending = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(whilePending.getTitle()).isEqualTo("Tên ban đầu");
        assertThat(whilePending.getDescription()).isEqualTo("Mô tả ban đầu");
        assertThat(whilePending.isAllowDownload()).isTrue();

        List<DocumentRagRequestResponse> pendingList = requestService.getPendingRequests();
        assertThat(pendingList).hasSize(1);
        assertThat(pendingList.getFirst().documentTitle()).isEqualTo("Tên ban đầu");
        assertThat(pendingList.getFirst().currentDescription()).isEqualTo("Mô tả ban đầu");
        assertThat(pendingList.getFirst().proposedTitle()).isEqualTo("Tên mới bị từ chối");
        assertThat(pendingList.getFirst().proposedDescription()).isNull();
        assertThat(pendingList.getFirst().reviewedByName()).isNull();

        requestService.rejectRequest(admin.getId(), req1Id, "Cần giữ mô tả hoặc sửa tên rõ hơn");

        Document afterReject = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(afterReject.getTitle()).isEqualTo("Tên ban đầu");
        assertThat(afterReject.getDescription()).isEqualTo("Mô tả ban đầu");
        assertThat(afterReject.isAllowDownload()).isTrue();

        UUID req2Id = requestService.createUpdateRequest(
                advisor.getId(),
                doc.getId(),
                new CreateDocumentUpdateRequest("Tên chính thức", null, false)
        );
        requestService.approveRequest(admin.getId(), req2Id);

        Document afterApprove = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(afterApprove.getTitle()).isEqualTo("Tên chính thức");
        assertThat(afterApprove.getDescription()).isNull();
        assertThat(afterApprove.isAllowDownload()).isFalse();
        assertThat(afterApprove.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ACTIVE);
        assertThat(afterApprove.getRagStatus()).isEqualTo(RagStatus.READY);
    }

    @Test
    void concurrentUpdateAndDeleteAllowOnlyOnePendingRequest() throws Exception {
        Document doc = createActiveDocument("Quy chế gốc", "Mô tả", true);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = new ArrayList<>();
        List<UUID> created = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> f1 = pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    UUID id = requestService.createUpdateRequest(
                            advisor.getId(),
                            doc.getId(),
                            new CreateDocumentUpdateRequest("Tên đồng thời", "Mô tả", true)
                    );
                    synchronized (created) {
                        created.add(id);
                    }
                } catch (Throwable ex) {
                    synchronized (errors) {
                        errors.add(ex);
                    }
                }
            });

            Future<?> f2 = pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    UUID id = requestService.createDeleteRequest(advisor.getId(), doc.getId());
                    synchronized (created) {
                        created.add(id);
                    }
                } catch (Throwable ex) {
                    synchronized (errors) {
                        errors.add(ex);
                    }
                }
            });

            ready.await();
            start.countDown();
            f1.get();
            f2.get();
        }

        assertThat(created).hasSize(1);
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst())
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(requestService.getPendingRequests()).hasSize(1);
    }

    @Test
    void concurrentAdminReviewsApplyOnlyFirstDecision() throws Exception {
        Account admin2 = accountRepository.save(new Account(
                "Admin Second",
                "admin2@hcmute.edu.vn",
                "hash",
                AccountRole.ADMIN,
                AccountAccessStatus.ACTIVE,
                false
        ));
        Document doc = createActiveDocument("Quy chế cạnh tranh", "Mô tả", true);
        UUID reqId = requestService.createUpdateRequest(
                advisor.getId(),
                doc.getId(),
                new CreateDocumentUpdateRequest("Tên mới", "Mô tả", true)
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> winners = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> f1 = pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    requestService.approveRequest(admin.getId(), reqId);
                    synchronized (winners) {
                        winners.add("APPROVED");
                    }
                } catch (Throwable ex) {
                    synchronized (errors) {
                        errors.add(ex);
                    }
                }
            });

            Future<?> f2 = pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    requestService.rejectRequest(admin2.getId(), reqId, "Từ chối");
                    synchronized (winners) {
                        winners.add("REJECTED");
                    }
                } catch (Throwable ex) {
                    synchronized (errors) {
                        errors.add(ex);
                    }
                }
            });

            ready.await();
            start.countDown();
            f1.get();
            f2.get();
        }

        assertThat(winners).hasSize(1);
        assertThat(errors).hasSize(1);
        assertThat(requestService.getPendingRequests()).isEmpty();

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
        Throwable loser = errors.getFirst();
        if (loser instanceof ResponseStatusException rse) {
            assertThat(exceptionHandler.handleResponseStatus(rse).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } else if (loser instanceof IllegalStateException ise) {
            assertThat(exceptionHandler.handleIllegalState(ise).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        } else {
            throw new AssertionError("Unexpected loser exception type", loser);
        }

        Document persistedDoc = documentRepository.findById(doc.getId()).orElseThrow();
        DocumentRagRequestResponse reviewed = requestService.getMyRequests(advisor.getId()).getFirst();
        if ("APPROVED".equals(winners.getFirst())) {
            assertThat(reviewed.status()).isEqualTo(DocumentRequestStatus.APPROVED);
            assertThat(reviewed.reviewedByName()).isEqualTo("Admin Reviewer");
            assertThat(reviewed.rejectionReason()).isNull();
            assertThat(persistedDoc.getTitle()).isEqualTo("Tên mới");
        } else {
            assertThat(winners.getFirst()).isEqualTo("REJECTED");
            assertThat(reviewed.status()).isEqualTo(DocumentRequestStatus.REJECTED);
            assertThat(reviewed.reviewedByName()).isEqualTo("Admin Second");
            assertThat(reviewed.rejectionReason()).isEqualTo("Từ chối");
            assertThat(persistedDoc.getTitle()).isEqualTo("Quy chế cạnh tranh");
        }
    }

    @Test
    void deleteWorkflowArchivesDocumentBlocksFileEndpointsAndIgnoresLateCallbacks() throws Exception {
        Document doc = createActiveDocument("Quy chế thu hồi", "Mô tả", true);
        when(documentStorageService.loadUtf8Text("extracted/" + doc.getId() + ".md")).thenReturn("# Noi dung");
        when(documentStorageService.loadBytes(doc.getStoragePath())).thenReturn("pdf-data".getBytes(StandardCharsets.UTF_8));

        UUID deleteReqId = requestService.createDeleteRequest(advisor.getId(), doc.getId());

        assertThat(documentService.getDocumentContent(student.getId(), doc.getId()).markdownContent()).isEqualTo("# Noi dung");
        assertThat(documentService.previewDocument(student.getId(), doc.getId()).content()).isNotEmpty();
        assertThat(documentService.downloadDocument(student.getId(), doc.getId()).content()).isNotEmpty();

        requestService.approveRequest(admin.getId(), deleteReqId);

        Document archived = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(archived.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ARCHIVED);
        assertThat(archived.getRagStatus()).isEqualTo(RagStatus.DISABLED);
        assertThat(archived.getArchivedAt()).isNotNull();

        List<DocumentRagRequestResponse> history = requestService.getMyRequests(advisor.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().requestType()).isEqualTo(DocumentRequestType.DELETE);
        assertThat(history.getFirst().status()).isEqualTo(DocumentRequestStatus.APPROVED);
        assertThat(history.getFirst().reviewedByName()).isEqualTo("Admin Reviewer");
        assertThat(history.getFirst().reviewedAt()).isNotNull();

        for (UUID actorId : List.of(student.getId(), advisor.getId(), admin.getId())) {
            assertThatThrownBy(() -> documentService.getDocumentContent(actorId, doc.getId()))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> documentService.previewDocument(actorId, doc.getId()))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> documentService.downloadDocument(actorId, doc.getId()))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }

        ragStatusService.markReady(doc.getId());
        ragStatusService.markFailed(doc.getId());

        Document afterLateCallbacks = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(afterLateCallbacks.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ARCHIVED);
        assertThat(afterLateCallbacks.getRagStatus()).isEqualTo(RagStatus.DISABLED);
        assertThatThrownBy(() -> documentService.downloadDocument(student.getId(), doc.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void addRegressionAndAdvisorDraftVisibilityWorkOnPostgres() {
        Document draftDoc = documentRepository.save(new Document(
                advisor,
                "Tài liệu ADD",
                "Mô tả ADD",
                true,
                "add.pdf",
                "documents/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                2048L,
                "b".repeat(64)
        ));

        assertThat(documentService.getDocuments(advisor.getId())).hasSize(1);
        assertThat(documentService.getDocuments(otherAdvisor.getId())).isEmpty();

        when(aiServiceClient.ingestFromStorage(any(AiServiceIngestRequest.class)))
                .thenReturn(new AiServiceIngestResponse("processing", draftDoc.getId().toString(), "Accepted"));

        UUID addReqId = requestService.createAddRequest(advisor.getId(), draftDoc.getId());
        requestService.approveRequest(admin.getId(), addReqId);
        ragStatusService.markReady(draftDoc.getId());

        Document readyDoc = documentRepository.findById(draftDoc.getId()).orElseThrow();
        assertThat(readyDoc.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ACTIVE);
        assertThat(readyDoc.getRagStatus()).isEqualTo(RagStatus.READY);
        assertThat(documentService.getDocuments(otherAdvisor.getId())).hasSize(1);
    }

    private void assertIsolatedTestSchema() {
        String currentSchema;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_schema()")) {
            currentSchema = rs.next() ? rs.getString(1) : null;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify isolated PostgreSQL test schema", ex);
        }

        String expectedSchema = PostgresTestSchemaExtension.getTestSchema();
        if (currentSchema == null
                || "public".equalsIgnoreCase(currentSchema)
                || !currentSchema.startsWith("sia_test_it_")
                || !currentSchema.equals(expectedSchema)) {
            throw new IllegalStateException(
                    "Refusing to run destructive test setup outside isolated schema; expected="
                            + expectedSchema + ", actual=" + currentSchema
            );
        }
    }

    private Document createActiveDocument(String title, String description, boolean allowDownload) {
        Document doc = new Document(
                advisor,
                title,
                description,
                allowDownload,
                "doc.pdf",
                "documents/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                2048L,
                "a".repeat(64)
        );
        doc.completeRagIndexing();
        return documentRepository.save(doc);
    }
}
