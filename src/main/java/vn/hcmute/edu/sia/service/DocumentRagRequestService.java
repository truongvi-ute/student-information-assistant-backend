package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.internal.DownloadedDocumentFile;
import java.util.UUID;
import java.util.List;
import vn.hcmute.edu.sia.dto.response.DocumentRagRequestResponse;

public interface DocumentRagRequestService {
    UUID createAddRequest(UUID advisorId, UUID documentId);
    List<DocumentRagRequestResponse> getMyRequests(UUID advisorId);
    List<DocumentRagRequestResponse> getPendingRequests();
    DownloadedDocumentFile getReviewFile(UUID adminId, UUID requestId);
    void approveRequest(UUID adminId, UUID requestId);
    void rejectRequest(UUID adminId, UUID requestId, String reason);
}