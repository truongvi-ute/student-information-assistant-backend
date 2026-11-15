package vn.hcmute.edu.sia.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.hcmute.edu.sia.enums.DocumentRequestStatus;
import vn.hcmute.edu.sia.enums.DocumentRequestType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DocumentRagRequestTest {

    private Document document;
    private Account advisor;
    private Account admin;
    private DocumentRagRequest request;

    @BeforeEach
    void setUp() {
        document = mock(Document.class);
        advisor = mock(Account.class);
        admin = mock(Account.class);
        request = new DocumentRagRequest(document, advisor);
    }

    @Test
    void newRequestStartsPending() {
        assertThat(request.getDocument()).isSameAs(document);
        assertThat(request.getRequestedByAdvisor()).isSameAs(advisor);
        assertThat(request.getRequestType()).isEqualTo(DocumentRequestType.ADD);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
    }

    @Test
    void requestRequiresDocumentAndAdvisor() {
        assertThatThrownBy(() -> new DocumentRagRequest(null, advisor)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DocumentRagRequest(document, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void approveRecordsReviewerAndTime() {
        request.approve(admin);

        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.APPROVED);
        assertThat(request.getReviewedByAdmin()).isSameAs(admin);
        assertThat(request.getReviewedAt()).isNotNull();
    }

    @Test
    void rejectRecordsTrimmedReasonReviewerAndTime() {
        request.reject(admin, "  Incomplete document  ");

        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Incomplete document");
        assertThat(request.getReviewedByAdmin()).isSameAs(admin);
        assertThat(request.getReviewedAt()).isNotNull();
    }

    @Test
    void rejectRequiresReasonAndKeepsRequestPending() {
        assertThatThrownBy(() -> request.reject(admin, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request.reject(admin, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
        assertThat(request.getReviewedByAdmin()).isNull();
    }

    @Test
    void reviewRequiresAdmin() {
        assertThatThrownBy(() -> request.approve(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> request.reject(null, "Reason")).isInstanceOf(NullPointerException.class);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.PENDING);
    }

    @Test
    void approvedRequestCannotBeReviewedAgain() {
        request.approve(admin);

        assertThatThrownBy(() -> request.approve(admin)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.reject(admin, "Reason")).isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.APPROVED);
    }

    @Test
    void rejectedRequestCannotBeReviewedAgain() {
        request.reject(admin, "Reason");

        assertThatThrownBy(() -> request.approve(admin)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.reject(admin, "Another reason")).isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(DocumentRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Reason");
    }
}
