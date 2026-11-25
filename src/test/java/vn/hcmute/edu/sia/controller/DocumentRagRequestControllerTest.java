package vn.hcmute.edu.sia.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.DocumentRagRequestService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;
import vn.hcmute.edu.sia.dto.request.CreateDocumentUpdateRequest;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.exception.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void createUpdateRequestRejectsMissingDescriptionKeyWithBadRequest() throws Exception {
        DocumentRagRequestService service = mock(DocumentRagRequestService.class);
        UUID advisorId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMvc mockMvc = buildAdvisorMockMvc(service, advisorId);

        String rawJson = """
                {
                  "title": "Quy chế mới",
                  "allowDownload": true
                }
                """;

        mockMvc.perform(post("/api/document-requests/documents/{documentId}/update", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createUpdateRequestAcceptsExplicitNullDescriptionAsClearIntent() throws Exception {
        DocumentRagRequestService service = mock(DocumentRagRequestService.class);
        UUID advisorId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        MockMvc mockMvc = buildAdvisorMockMvc(service, advisorId);
        when(service.createUpdateRequest(eq(advisorId), eq(documentId), any(CreateDocumentUpdateRequest.class)))
                .thenReturn(requestId);

        String rawJson = """
                {
                  "title": "Quy chế mới",
                  "description": null,
                  "allowDownload": false
                }
                """;

        mockMvc.perform(post("/api/document-requests/documents/{documentId}/update", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateDocumentUpdateRequest> captor =
                ArgumentCaptor.forClass(CreateDocumentUpdateRequest.class);
        verify(service).createUpdateRequest(eq(advisorId), eq(documentId), captor.capture());
        assertThat(captor.getValue().isDescriptionPresent()).isTrue();
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(captor.getValue().getTitle()).isEqualTo("Quy chế mới");
        assertThat(captor.getValue().getAllowDownload()).isFalse();
    }

    @Test
    void createUpdateRequestAcceptsBlankDescriptionStringAsExplicitField() throws Exception {
        DocumentRagRequestService service = mock(DocumentRagRequestService.class);
        UUID advisorId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        MockMvc mockMvc = buildAdvisorMockMvc(service, advisorId);
        when(service.createUpdateRequest(eq(advisorId), eq(documentId), any(CreateDocumentUpdateRequest.class)))
                .thenReturn(requestId);

        String rawJson = """
                {
                  "title": "Quy chế mới",
                  "description": "   ",
                  "allowDownload": true
                }
                """;

        mockMvc.perform(post("/api/document-requests/documents/{documentId}/update", documentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateDocumentUpdateRequest> captor =
                ArgumentCaptor.forClass(CreateDocumentUpdateRequest.class);
        verify(service).createUpdateRequest(eq(advisorId), eq(documentId), captor.capture());
        assertThat(captor.getValue().isDescriptionPresent()).isTrue();
        assertThat(captor.getValue().getDescription()).isEqualTo("   ");
    }

    @Test
    void createUpdateRequestRejectsMissingAllowDownloadWithBadRequest() throws Exception {
        DocumentRagRequestService service = mock(DocumentRagRequestService.class);
        MockMvc mockMvc = buildAdvisorMockMvc(service, UUID.randomUUID());

        String rawJson = """
                {
                  "title": "Quy chế mới",
                  "description": "Mô tả"
                }
                """;

        mockMvc.perform(post("/api/document-requests/documents/{documentId}/update", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createUpdateRequestRejectsMalformedJsonWithBadRequest() throws Exception {
        DocumentRagRequestService service = mock(DocumentRagRequestService.class);
        MockMvc mockMvc = buildAdvisorMockMvc(service, UUID.randomUUID());

        mockMvc.perform(post("/api/document-requests/documents/{documentId}/update", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void createDeleteRequestReturnsCreated() throws Exception {
        DocumentRagRequestService service = mock(DocumentRagRequestService.class);
        UUID advisorId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        MockMvc mockMvc = buildAdvisorMockMvc(service, advisorId);
        when(service.createDeleteRequest(advisorId, documentId)).thenReturn(requestId);

        mockMvc.perform(post("/api/document-requests/documents/{documentId}/delete", documentId))
                .andExpect(status().isCreated());

        verify(service).createDeleteRequest(advisorId, documentId);
    }

    private MockMvc buildAdvisorMockMvc(DocumentRagRequestService service, UUID advisorId) {
        Account advisor = mock(Account.class);
        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getEmail()).thenReturn("advisor@test.edu.vn");
        when(advisor.getPasswordHash()).thenReturn("hash");
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getAccessStatus()).thenReturn(AccountAccessStatus.ACTIVE);
        when(advisor.isPasswordChangeRequired()).thenReturn(false);

        AccountPrincipal principal = new AccountPrincipal(advisor);
        return MockMvcBuilders.standaloneSetup(new DocumentRagRequestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(new JsonMapper()))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return AccountPrincipal.class.isAssignableFrom(parameter.getParameterType());
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return principal;
                    }
                })
                .build();
    }
}
