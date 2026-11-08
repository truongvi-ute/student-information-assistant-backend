package vn.hcmute.edu.sia.service;

import java.util.UUID;

import vn.hcmute.edu.sia.dto.request.UploadDocumentRequest;
import vn.hcmute.edu.sia.dto.response.DocumentUploadResponse;

public interface DocumentService {
    DocumentUploadResponse uploadDocument(UUID advisorId,UploadDocumentRequest request);
}
