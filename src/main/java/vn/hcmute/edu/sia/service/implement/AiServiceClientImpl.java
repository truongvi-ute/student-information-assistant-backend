package vn.hcmute.edu.sia.service.implement;

import java.net.SocketTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatResponse;
import vn.hcmute.edu.sia.exception.AiServiceException;
import vn.hcmute.edu.sia.service.AiServiceClient;

@Service
@RequiredArgsConstructor
public class AiServiceClientImpl implements AiServiceClient {

    private final RestClient aiServiceRestClient;

    @Override
    public AiServiceChatResponse chat(AiServiceChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }

        AiServiceChatResponse response;
        try {
            response = aiServiceRestClient.post()
                    .uri("/v1/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiServiceChatResponse.class);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                throw new AiServiceException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI service timed out while generating answer",
                        exception
                );
            }
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "AI service returned an error response",
                    exception
            );
        } catch (ResourceAccessException exception) {
            if (exception.getCause() instanceof SocketTimeoutException) {
                throw new AiServiceException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI service timed out while generating answer",
                        exception
                );
            }
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not connect to AI service",
                    exception
            );
        } catch (RestClientException exception) {
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to communicate with AI service",
                    exception
            );
        }

        if (response == null || response.answer() == null || response.answer().isBlank()) {
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "AI service returned an empty answer"
            );
        }

        return response;
    }
}
