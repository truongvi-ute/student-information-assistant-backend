package vn.hcmute.edu.sia.dto.request;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAiConversationRequest(
        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,
        UUID scopeMajorId
) {}
