package vn.hcmute.edu.sia.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.DocumentRagStatusCallbackRequest;
import vn.hcmute.edu.sia.service.DocumentRagStatusService;

@RestController
@RequestMapping("/api/internal/documents")
@RequiredArgsConstructor
public class InternalDocumentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalDocumentController.class);
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final DocumentRagStatusService documentRagStatusService;

    @Value("${app.ai-service.internal-api-key:sia-internal-secret-change-me}")
    private String internalApiKey;

    @PostMapping("/{documentId}/rag-status")
    public ResponseEntity<Void> updateRagStatus(
            @PathVariable UUID documentId,
            @RequestParam(required = false) UUID attemptId,
            @RequestHeader(value = INTERNAL_API_KEY_HEADER, required = false) String providedApiKey,
            @Valid @RequestBody DocumentRagStatusCallbackRequest request
    ) {
        if (!isValidInternalApiKey(providedApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }

        String normalizedStatus = request.status().trim().toUpperCase(Locale.ROOT);
        switch (normalizedStatus) {
            case "READY" -> {
                if (attemptId == null) {
                    documentRagStatusService.markReady(documentId);
                } else {
                    documentRagStatusService.markReady(documentId, attemptId);
                }
                LOGGER.info(
                        "Marked document {} as READY (totalChunks={})",
                        documentId,
                        request.totalChunks()
                );
            }
            case "FAILED" -> {
                if (attemptId == null) {
                    documentRagStatusService.markFailed(documentId);
                } else {
                    documentRagStatusService.markFailed(documentId, attemptId);
                }
                LOGGER.warn(
                        "Marked document {} as FAILED (reason={})",
                        documentId,
                        request.errorMessage()
                );
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported RAG callback status: " + request.status()
            );
        }

        return ResponseEntity.noContent().build();
    }

    private boolean isValidInternalApiKey(String providedApiKey) {
        if (providedApiKey == null || internalApiKey == null || internalApiKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8),
                providedApiKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
