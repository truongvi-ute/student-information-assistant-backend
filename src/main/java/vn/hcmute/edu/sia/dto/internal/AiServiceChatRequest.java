package vn.hcmute.edu.sia.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiServiceChatRequest(
        String question,
        @JsonProperty("top_k") int topK,
        @JsonProperty("filename_filter") String filenameFilter,
        List<AiServiceChatHistoryMessage> history,
        @JsonProperty("allowed_document_ids") List<String> allowedDocumentIds
) {
    public AiServiceChatRequest(String question, int topK, String filenameFilter) {
        this(question, topK, filenameFilter, List.of(), null);
    }

    public AiServiceChatRequest(
            String question,
            int topK,
            String filenameFilter,
            List<AiServiceChatHistoryMessage> history
    ) {
        this(question, topK, filenameFilter, history, null);
    }

    public AiServiceChatRequest {
        history = history != null ? List.copyOf(history) : List.of();
        allowedDocumentIds = allowedDocumentIds != null ? List.copyOf(allowedDocumentIds) : null;
    }
}
