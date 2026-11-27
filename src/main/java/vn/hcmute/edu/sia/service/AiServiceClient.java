package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.internal.AiServiceChatRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatResponse;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceIngestResponse;

public interface AiServiceClient {
    AiServiceChatResponse chat(AiServiceChatRequest request);

    AiServiceIngestResponse ingestFromStorage(AiServiceIngestRequest request);

    void deleteDocumentVectors(String documentId);
}
