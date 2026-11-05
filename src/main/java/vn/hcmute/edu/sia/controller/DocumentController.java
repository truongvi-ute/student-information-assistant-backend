package vn.hcmute.edu.sia.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('ADVISOR', 'ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getDocuments() {
        List<DocumentResponse> response = documentService.getDocuments();
        return ResponseEntity.ok(response);
    }
}
