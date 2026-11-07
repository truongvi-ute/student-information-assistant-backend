package vn.hcmute.edu.sia.service;

import java.util.List;
import java.util.UUID;

import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
import vn.hcmute.edu.sia.dto.response.DocumentResponse;
import vn.hcmute.edu.sia.dto.response.DocumentUploadResponse;

public interface DocumentService {

    DocumentUploadResponse uploadDocument(UUID uploaderId, UploadDocumentRequest request);

    List<DocumentResponse> getDocuments();
}
