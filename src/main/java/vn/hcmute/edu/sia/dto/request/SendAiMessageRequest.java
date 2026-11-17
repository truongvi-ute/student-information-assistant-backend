package vn.hcmute.edu.sia.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAiMessageRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(max = 2000, message = "Question must not exceed 2000 characters")
        String question,

        @Min(value = 1, message = "topK must be between 1 and 20")
        @Max(value = 20, message = "topK must be between 1 and 20")
        Integer topK,

        String filenameFilter
) {}
