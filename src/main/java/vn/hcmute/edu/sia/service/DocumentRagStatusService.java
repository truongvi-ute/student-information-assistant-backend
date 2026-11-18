package vn.hcmute.edu.sia.service;

import java.util.UUID;

public interface DocumentRagStatusService {

    void markReady(UUID documentId);

    void markFailed(UUID documentId);
}
