package vn.hcmute.edu.sia.controller;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import java.util.UUID;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.DocumentRagRequestService;
import vn.hcmute.edu.sia.dto.request.CreateDocumentUpdateRequest;
import vn.hcmute.edu.sia.dto.response.DocumentRagRequestResponse;
import vn.hcmute.edu.sia.dto.request.RejectDocumentRagRequest;

@RestController
@RequestMapping("/api/document-requests")
@RequiredArgsConstructor
public class DocumentRagRequestController {
    private final DocumentRagRequestService requestService;

    @PostMapping("/documents/{documentId}")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<UUID> createAddRequest(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId
    ) {
        UUID requestId = requestService.createAddRequest(principal.getAccountId(), documentId);

        return ResponseEntity.status(HttpStatus.CREATED).body(requestId);
    }

    @PostMapping("/documents/{documentId}/update")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<UUID> createUpdateRequest(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId,
            @Valid @RequestBody CreateDocumentUpdateRequest body
    ) {
        UUID requestId = requestService.createUpdateRequest(principal.getAccountId(), documentId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(requestId);
    }

    @PostMapping("/documents/{documentId}/delete")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<UUID> createDeleteRequest(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId
    ) {
        UUID requestId = requestService.createDeleteRequest(principal.getAccountId(), documentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(requestId);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<List<DocumentRagRequestResponse>> getMyRequests(
            @AuthenticationPrincipal AccountPrincipal principal
    ) {
        List<DocumentRagRequestResponse> response = requestService.getMyRequests(
                principal.getAccountId()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentRagRequestResponse>> getPendingRequests() {
        return ResponseEntity.ok(requestService.getPendingRequests());
    }

    @GetMapping("/{requestId}/file")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getReviewFile(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID requestId
    ) {
        DownloadedDocumentFile file = requestService.getReviewFile(principal.getAccountId(), requestId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveRequest(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID requestId
    ) {
        requestService.approveRequest(principal.getAccountId(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectRequest(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectDocumentRagRequest body
    ) {
        requestService.rejectRequest(
                principal.getAccountId(),
                requestId,
                body.reason()
        );

        return ResponseEntity.noContent().build();
    }
}