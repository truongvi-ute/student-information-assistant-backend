package vn.hcmute.edu.sia.service.implement;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;

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
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestResponse;
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

        AiServiceChatResponse response = executePost(
                "/v1/chat",
                request,
                AiServiceChatResponse.class,
                "generating answer"
        );

        if (response == null || response.answer() == null || response.answer().isBlank()) {
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "AI service returned an empty answer"
            );
        }

        return response;
    }

    @Override
    public AiServiceIngestResponse ingestFromStorage(AiServiceIngestRequest request) {
        if (request == null
                || request.documentId() == null || request.documentId().isBlank()
                || request.fileKey() == null || request.fileKey().isBlank()
                || request.filename() == null || request.filename().isBlank()) {
            throw new IllegalArgumentException("Document ID, file key, and filename must not be blank");
        }

        AiServiceIngestResponse response = executePost(
                "/v1/documents/ingest-from-storage",
                request,
                AiServiceIngestResponse.class,
                "starting document ingestion"
        );

        if (response == null || response.status() == null || response.status().isBlank()) {
            throw new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "AI service returned an empty ingestion response"
            );
        }

        return response;
    }

    private <T> T executePost(
            String uri,
            Object body,
            Class<T> responseType,
            String timeoutContext
    ) {
        return handleUpstreamErrors(
                () -> aiServiceRestClient.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(responseType),
                timeoutContext
        );
    }

    private <T> T handleUpstreamErrors(Supplier<T> action, String timeoutContext) {
        try {
            return action.get();
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
                throw new AiServiceException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI service timed out while " + timeoutContext,
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
                        "AI service timed out while " + timeoutContext,
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
    }
}
