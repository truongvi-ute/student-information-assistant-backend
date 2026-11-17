package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.internal.AiServiceChatRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatResponse;

public interface AiServiceClient {
    AiServiceChatResponse chat(AiServiceChatRequest request);
}
