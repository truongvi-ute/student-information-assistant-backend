package vn.hcmute.edu.sia.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
import vn.hcmute.edu.sia.dto.request.CreateDocumentUpdateRequest;
import vn.hcmute.edu.sia.dto.response.DocumentContentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentUploadResponse;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADVISOR', 'ADMIN')")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @ModelAttribute UploadDocumentRequest request
    ) {
        DocumentUploadResponse response = documentService.uploadDocument(
                principal.getAccountId(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{documentId}/draft")
    @PreAuthorize("hasAnyRole('ADVISOR', 'ADMIN')")
    public ResponseEntity<Void> updateDraft(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId,
            @Valid @RequestBody CreateDocumentUpdateRequest request
    ) {
        documentService.updateDraft(principal.getAccountId(), documentId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{documentId}/draft")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<Void> deleteDraft(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId
    ) {
        documentService.deleteDraft(principal.getAccountId(), documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADVISOR', 'ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @AuthenticationPrincipal AccountPrincipal principal
    ) {
        List<DocumentResponse> response = documentService.getDocuments(principal.getAccountId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentContentResponse> getDocumentContent(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId
    ) {
        DocumentContentResponse response = documentService.getDocumentContent(
                principal.getAccountId(),
                documentId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> previewDocument(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId
    ) {
        DownloadedDocumentFile file = documentService.previewDocument(
                principal.getAccountId(),
                documentId
        );
        return buildFileResponse(
                file,
                ContentDisposition.inline()
                        .filename(file.originalFilename(), StandardCharsets.UTF_8)
                        .build()
        );
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadDocument(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID documentId
    ) {
        DownloadedDocumentFile file = documentService.downloadDocument(
                principal.getAccountId(),
                documentId
        );
        return buildFileResponse(
                file,
                ContentDisposition.attachment()
                        .filename(file.originalFilename(), StandardCharsets.UTF_8)
                        .build()
        );
    }

    private ResponseEntity<byte[]> buildFileResponse(
            DownloadedDocumentFile file,
            ContentDisposition disposition
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Allow-Download", String.valueOf(file.allowDownload()))
                .body(file.content());
    }
}
