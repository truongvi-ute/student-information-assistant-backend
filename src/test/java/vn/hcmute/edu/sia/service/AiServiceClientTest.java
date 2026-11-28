package vn.hcmute.edu.sia.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatHistoryMessage;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatResponse;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestResponse;
import vn.hcmute.edu.sia.exception.AiServiceException;
import vn.hcmute.edu.sia.service.implement.AiServiceClientImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceClientTest {

    private MockRestServiceServer mockServer;
    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        aiServiceClient = new AiServiceClientImpl(builder.build());
    }

    @Test
    @DisplayName("Gọi POST /v1/chat thành công và ánh xạ đúng JSON snake_case")
    void chat_Success_MapsSnakeCaseRequestAndResponse() {
        String responseJson = """
                {
                  "question": "Điều kiện tốt nghiệp là gì?",
                  "answer": "Sinh viên cần tích lũy đủ tín chỉ và chuẩn đầu ra ngoại ngữ.",
                  "sources": [
                    {
                      "document_id": "11111111-1111-1111-1111-111111111111",
                      "filename": "quy_che_2026.pdf",
                      "page_number": 5,
                      "chunk_index": 3,
                      "char_start": 120,
                      "char_end": 260,
                      "score": 0.912,
                      "snippet": "Điều 14: Điều kiện xét tốt nghiệp...",
                      "content": "Điều 14: Điều kiện xét tốt nghiệp đại học chính quy."
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.question").value("Điều kiện tốt nghiệp là gì?"))
                .andExpect(jsonPath("$.top_k").value(5))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history.length()").value(0))
                .andExpect(jsonPath("$.filename_filter").doesNotExist())
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        AiServiceChatResponse response = aiServiceClient.chat(
                new AiServiceChatRequest("Điều kiện tốt nghiệp là gì?", 5)
        );

        mockServer.verify();
        assertThat(response.answer()).contains("chuẩn đầu ra ngoại ngữ");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().documentId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(response.sources().getFirst().filename()).isEqualTo("quy_che_2026.pdf");
        assertThat(response.sources().getFirst().pageNumber()).isEqualTo(5);
        assertThat(response.sources().getFirst().chunkIndex()).isEqualTo(3);
        assertThat(response.sources().getFirst().charStart()).isEqualTo(120);
        assertThat(response.sources().getFirst().charEnd()).isEqualTo(260);
        assertThat(response.sources().getFirst().content()).isEqualTo("Điều 14: Điều kiện xét tốt nghiệp đại học chính quy.");
        assertThat(response.sources().getFirst().score()).isEqualTo(0.912);
    }

    @Test
    @DisplayName("Gửi kèm mảng history (role, content) trong POST /v1/chat")
    void chat_WithHistory_SerializesHistoryRolesAndContents() {
        String responseJson = """
                {
                  "question": "Còn chuẩn đầu ra của ngành đó?",
                  "answer": "Chuẩn đầu ra ngành Kỹ thuật dữ liệu gồm chuẩn ngoại ngữ và tin học.",
                  "sources": []
                }
                """;

        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.question").value("Còn chuẩn đầu ra của ngành đó?"))
                .andExpect(jsonPath("$.top_k").value(5))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].role").value("user"))
                .andExpect(jsonPath("$.history[0].content").value("Ngành Kỹ thuật dữ liệu học mấy năm?"))
                .andExpect(jsonPath("$.history[1].role").value("assistant"))
                .andExpect(jsonPath("$.history[1].content").value("Ngành Kỹ thuật dữ liệu đào tạo 4 năm."))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        AiServiceChatResponse response = aiServiceClient.chat(new AiServiceChatRequest(
                "Còn chuẩn đầu ra của ngành đó?",
                5,
                List.of(
                        new AiServiceChatHistoryMessage("user", "Ngành Kỹ thuật dữ liệu học mấy năm?"),
                        new AiServiceChatHistoryMessage("assistant", "Ngành Kỹ thuật dữ liệu đào tạo 4 năm.")
                )
        ));

        mockServer.verify();
        assertThat(response.answer()).contains("Chuẩn đầu ra");
    }

    @Test
    @DisplayName("Ném AiServiceException GATEWAY_TIMEOUT (504) khi sia-ai-service trả 504")
    void chat_Upstream504_ThrowsGatewayTimeout() {
        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> aiServiceClient.chat(new AiServiceChatRequest("Câu hỏi", 5)))
                .isInstanceOf(AiServiceException.class)
                .extracting(ex -> ((AiServiceException) ex).getStatus())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @DisplayName("Ném AiServiceException BAD_GATEWAY (502) khi sia-ai-service trả 502")
    void chat_Upstream502_ThrowsBadGateway() {
        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> aiServiceClient.chat(new AiServiceChatRequest("Câu hỏi", 5)))
                .isInstanceOf(AiServiceException.class)
                .extracting(ex -> ((AiServiceException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("Gọi POST /v1/documents/ingest-from-storage thành công và ánh xạ snake_case")
    void ingestFromStorage_Success_MapsSnakeCasePayloadAndResponse() {
        String responseJson = """
                {
                  "status": "processing",
                  "document_id": "doc-123",
                  "message": "Accepted"
                }
                """;

        mockServer.expect(requestTo("http://localhost:8000/v1/documents/ingest-from-storage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.document_id").value("doc-123"))
                .andExpect(jsonPath("$.file_key").value("documents/doc-123.pdf"))
                .andExpect(jsonPath("$.filename").value("quy_che.pdf"))
                .andExpect(jsonPath("$.callback_url").value("http://localhost:8080/api/internal/documents/doc-123/rag-status"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(responseJson));

        AiServiceIngestResponse response = aiServiceClient.ingestFromStorage(new AiServiceIngestRequest(
                "doc-123",
                "documents/doc-123.pdf",
                "quy_che.pdf",
                "http://localhost:8080/api/internal/documents/doc-123/rag-status",
                Map.of("title", "Quy chế")
        ));

        mockServer.verify();
        assertThat(response.status()).isEqualTo("processing");
        assertThat(response.documentId()).isEqualTo("doc-123");
    }

    @Test
    @DisplayName("Từ chối gọi ingestFromStorage khi thiếu documentId hoặc fileKey")
    void ingestFromStorage_BlankFields_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> aiServiceClient.ingestFromStorage(
                new AiServiceIngestRequest(" ", "documents/a.pdf", "a.pdf", null, Map.of())
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Gửi kèm allowed_document_ids (kể cả rỗng) trong POST /v1/chat")
    void chat_WithAllowedDocumentIds_SerializesWhitelist() {
        String responseJson = """
                {
                  "question": "Quy chế?",
                  "answer": "Không tìm thấy thông tin.",
                  "sources": []
                }
                """;

        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.allowed_document_ids").isArray())
                .andExpect(jsonPath("$.allowed_document_ids.length()").value(1))
                .andExpect(jsonPath("$.allowed_document_ids[0]").value("doc-active-1"))
                .andExpect(jsonPath("$.filename_filter").doesNotExist())
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        aiServiceClient.chat(new AiServiceChatRequest(
                "Quy chế?",
                5,
                List.of(),
                List.of("doc-active-1")
        ));

        mockServer.verify();
    }

    @Test
    @DisplayName("Gọi DELETE /v1/documents/{documentId} để xóa vector trên Qdrant")
    void deleteDocumentVectors_Success_CallsDeleteEndpoint() {
        mockServer.expect(requestTo("http://localhost:8000/v1/documents/doc-archive-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"status\":\"deleted\",\"document_id\":\"doc-archive-1\",\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        aiServiceClient.deleteDocumentVectors("doc-archive-1");

        mockServer.verify();
    }

    @Test
    @DisplayName("Từ chối deleteDocumentVectors khi documentId rỗng và ánh xạ lỗi 502")
    void deleteDocumentVectors_BlankAndUpstreamError() {
        assertThatThrownBy(() -> aiServiceClient.deleteDocumentVectors("   "))
                .isInstanceOf(IllegalArgumentException.class);

        mockServer.expect(requestTo("http://localhost:8000/v1/documents/doc-err"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> aiServiceClient.deleteDocumentVectors("doc-err"))
                .isInstanceOf(AiServiceException.class)
                .extracting(ex -> ((AiServiceException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
