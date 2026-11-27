package vn.hcmute.edu.sia.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.DocumentLifecycleStatus;
import vn.hcmute.edu.sia.enums.RagStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    private Account advisor;
    private Account admin;
    private Account student;

    @BeforeEach
    void setUp() {
        advisor = new Account("Advisor One", "advisor@test.edu.vn", "hash", AccountRole.ADVISOR, AccountAccessStatus.ACTIVE, false);
        admin = new Account("Admin One", "admin@test.edu.vn", "hash", AccountRole.ADMIN, AccountAccessStatus.ACTIVE, false);
        student = new Account("Student One", "student@test.edu.vn", "hash", AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
    }

    @Test
    @DisplayName("Tạo Document thành công với tài khoản Advisor")
    void createDocument_Advisor_Success() {
        Document doc = new Document(advisor, "Quy chế 2024", "Mô tả", true, "qc.pdf", "path/qc.pdf", "application/pdf", 1024L, "a".repeat(64));

        assertThat(doc.getTitle()).isEqualTo("Quy chế 2024");
        assertThat(doc.getRagStatus()).isEqualTo(RagStatus.NOT_INDEXED);
        assertThat(doc.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.DRAFT);
        assertThat(doc.isAllowDownload()).isTrue();
    }

    @Test
    void createDocument_Admin_Success() {
        Document document = new Document(admin, "Quy chế chung", null, false, "qc.pdf", "path/qc.pdf", "application/pdf", 2048L, "b".repeat(64));
        assertThat(document.getUploadedBy()).isSameAs(admin);
    }

    @Test
    @DisplayName("Tạo Document thất bại khi uploader là Student")
    void createDocument_Student_ThrowsException() {
        assertThatThrownBy(() -> new Document(student, "Tiêu đề", null, true, "f.pdf", "p", "application/pdf", 1024L, "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only an advisor or admin can upload a document");
    }

    @Test
    @DisplayName("Chuyển trạng thái RAG sang PROCESSING, READY và hoàn tất vòng đời")
    void ragLifecycle_Success() {
        Document doc = new Document(advisor, "Quy chế", null, true, "f.pdf", "p", "application/pdf", 1024L, "a".repeat(64));

        doc.startRagProcessing();
        assertThat(doc.getRagStatus()).isEqualTo(RagStatus.PROCESSING);

        doc.completeRagIndexing();
        assertThat(doc.getRagStatus()).isEqualTo(RagStatus.READY);
        assertThat(doc.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ACTIVE);
        assertThat(doc.isEligibleForRag()).isTrue();
    }

    @Test
    @DisplayName("Lưu trữ tài liệu chuyển trạng thái sang ARCHIVED và vô hiệu hóa RAG")
    void archiveDocument_Success() {
        Document doc = new Document(advisor, "Quy chế cũ", null, true, "f.pdf", "p", "application/pdf", 1024L, "a".repeat(64));
        doc.completeRagIndexing();

        doc.archive();

        assertThat(doc.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ARCHIVED);
        assertThat(doc.getRagStatus()).isEqualTo(RagStatus.DISABLED);
        assertThat(doc.getArchivedAt()).isNotNull();
        assertThat(doc.isDownloadable()).isFalse();
    }

    @Test
    @DisplayName("Áp dụng UPDATE cập nhật đủ ba trường, cho phép xóa trắng mô tả và giữ nguyên ragStatus")
    void applyApprovedUpdate_ClearsDescriptionAndPreservesRagStatus() {
        Document doc = new Document(advisor, "Tên cũ", "Mô tả cũ", true, "f.pdf", "p", "application/pdf", 1024L, "a".repeat(64));
        doc.completeRagIndexing();

        doc.applyApprovedUpdate("  Tên mới  ", null, false);

        assertThat(doc.getTitle()).isEqualTo("Tên mới");
        assertThat(doc.getDescription()).isNull();
        assertThat(doc.isAllowDownload()).isFalse();
        assertThat(doc.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ACTIVE);
        assertThat(doc.getRagStatus()).isEqualTo(RagStatus.READY);
    }

    @Test
    @DisplayName("Từ chối applyApprovedUpdate nếu tài liệu không ở trạng thái ACTIVE")
    void applyApprovedUpdate_NonActive_ThrowsIllegalState() {
        Document draft = new Document(advisor, "Nháp", "Mô tả", true, "f.pdf", "p", "application/pdf", 1024L, "a".repeat(64));

        assertThatThrownBy(() -> draft.applyApprovedUpdate("Tên mới", null, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Callback READY hoặc FAILED không làm thay đổi tài liệu đã ARCHIVED")
    void archivedDocument_IgnoresRagCallbacks() {
        Document doc = new Document(advisor, "Quy chế cũ", null, true, "f.pdf", "p", "application/pdf", 1024L, "a".repeat(64));
        doc.completeRagIndexing();
        doc.archive();

        doc.completeRagIndexing();
        doc.failRagIndexing();

        assertThat(doc.getLifecycleStatus()).isEqualTo(DocumentLifecycleStatus.ARCHIVED);
        assertThat(doc.getRagStatus()).isEqualTo(RagStatus.DISABLED);
        assertThat(doc.isDownloadable()).isFalse();
    }
}

