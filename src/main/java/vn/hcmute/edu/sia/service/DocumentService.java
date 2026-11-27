package vn.hcmute.edu.sia.service;

import java.util.List;
import java.util.UUID;

import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
import vn.hcmute.edu.sia.dto.request.CreateDocumentUpdateRequest;
import vn.hcmute.edu.sia.dto.response.DocumentContentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentUploadResponse;

public interface DocumentService {

    DocumentUploadResponse uploadDocument(UUID uploaderId, UploadDocumentRequest request);

    void updateDraft(UUID requesterId, UUID documentId, CreateDocumentUpdateRequest request);

    void deleteDraft(UUID advisorId, UUID documentId);

    List<DocumentResponse> getDocuments(UUID requesterId);

    DocumentContentResponse getDocumentContent(UUID requesterId, UUID documentId);

    DownloadedDocumentFile previewDocument(UUID requesterId, UUID documentId);

    DownloadedDocumentFile downloadDocument(UUID requesterId, UUID documentId);
}
