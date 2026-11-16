package vn.hcmute.edu.sia.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatResponse;
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
                      "filename": "quy_che_2026.pdf",
                      "page_number": 5,
                      "chunk_index": 3,
                      "score": 0.912,
                      "snippet": "Điều 14: Điều kiện xét tốt nghiệp..."
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.question").value("Điều kiện tốt nghiệp là gì?"))
                .andExpect(jsonPath("$.top_k").value(5))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        AiServiceChatResponse response = aiServiceClient.chat(
                new AiServiceChatRequest("Điều kiện tốt nghiệp là gì?", 5, null)
        );

        mockServer.verify();
        assertThat(response.answer()).contains("chuẩn đầu ra ngoại ngữ");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().filename()).isEqualTo("quy_che_2026.pdf");
        assertThat(response.sources().getFirst().pageNumber()).isEqualTo(5);
        assertThat(response.sources().getFirst().chunkIndex()).isEqualTo(3);
        assertThat(response.sources().getFirst().score()).isEqualTo(0.912);
    }

    @Test
    @DisplayName("Ném AiServiceException GATEWAY_TIMEOUT (504) khi sia-ai-service trả 504")
    void chat_Upstream504_ThrowsGatewayTimeout() {
        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> aiServiceClient.chat(new AiServiceChatRequest("Câu hỏi", 5, null)))
                .isInstanceOf(AiServiceException.class)
                .extracting(ex -> ((AiServiceException) ex).getStatus())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    @DisplayName("Ném AiServiceException BAD_GATEWAY (502) khi sia-ai-service trả 502")
    void chat_Upstream502_ThrowsBadGateway() {
        mockServer.expect(requestTo("http://localhost:8000/v1/chat"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> aiServiceClient.chat(new AiServiceChatRequest("Câu hỏi", 5, null)))
                .isInstanceOf(AiServiceException.class)
                .extracting(ex -> ((AiServiceException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
