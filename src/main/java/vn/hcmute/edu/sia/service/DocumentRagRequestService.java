package vn.hcmute.edu.sia.service;

import java.util.UUID;
import java.util.List;
import vn.hcmute.edu.sia.dto.response.DocumentRagRequestResponse;

public interface DocumentRagRequestService {
    UUID createAddRequest(UUID advisorId, UUID documentId);
    List<DocumentRagRequestResponse> getMyRequests(UUID advisorId);
    List<DocumentRagRequestResponse> getPendingRequests();
    void approveRequest(UUID adminId, UUID requestId);
    void rejectRequest(UUID adminId, UUID requestId, String reason);
}