package vn.hcmute.edu.sia.controller;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.DocumentRagStatusCallbackRequest;
import vn.hcmute.edu.sia.service.DocumentRagStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InternalDocumentControllerTest {

    @Mock
    private DocumentRagStatusService documentRagStatusService;

    @InjectMocks
    private InternalDocumentController controller;

    private UUID documentId;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        ReflectionTestUtils.setField(controller, "internalApiKey", "secret-key");
    }

    @Test
    @DisplayName("Từ chối callback khi sai hoặc thiếu X-Internal-Api-Key")
    void updateRagStatus_InvalidApiKey_ThrowsUnauthorized() {
        var req = new DocumentRagStatusCallbackRequest("READY", 5, null);

        assertThatThrownBy(() -> controller.updateRagStatus(documentId, "wrong-key", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(documentRagStatusService);
    }

    @Test
    @DisplayName("Callback READY cập nhật trạng thái READY qua DocumentRagStatusService")
    void updateRagStatus_Ready_MarksReady() {
        var req = new DocumentRagStatusCallbackRequest("READY", 5, null);

        ResponseEntity<Void> response = controller.updateRagStatus(documentId, "secret-key", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(documentRagStatusService).markReady(documentId);
    }

    @Test
    @DisplayName("Callback FAILED cập nhật trạng thái FAILED qua DocumentRagStatusService")
    void updateRagStatus_Failed_MarksFailed() {
        var req = new DocumentRagStatusCallbackRequest("FAILED", 0, "RustFS error");

        ResponseEntity<Void> response = controller.updateRagStatus(documentId, "secret-key", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(documentRagStatusService).markFailed(documentId);
    }

    @Test
    @DisplayName("Trạng thái callback không hợp lệ trả về 400 BAD_REQUEST")
    void updateRagStatus_UnsupportedStatus_ThrowsBadRequest() {
        var req = new DocumentRagStatusCallbackRequest("UNKNOWN", 0, null);

        assertThatThrownBy(() -> controller.updateRagStatus(documentId, "secret-key", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
