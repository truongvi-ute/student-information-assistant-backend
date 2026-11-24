package vn.hcmute.edu.sia.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.DocumentRagRequestService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DocumentRagRequestControllerTest {
    @Test
    void reviewFileReturnsAttachmentWithoutCaching() {
        DocumentRagRequestService service = mock(DocumentRagRequestService.class);
        AccountPrincipal principal = mock(AccountPrincipal.class);
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(principal.getAccountId()).thenReturn(adminId);
        when(service.getReviewFile(adminId, requestId)).thenReturn(
                new DownloadedDocumentFile("review.docx", "application/octet-stream", new byte[]{1, 2}, false));

        var response = new DocumentRagRequestController(service).getReviewFile(principal, requestId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("review.docx");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).containsExactly((byte) 1, (byte) 2);
    }
}
