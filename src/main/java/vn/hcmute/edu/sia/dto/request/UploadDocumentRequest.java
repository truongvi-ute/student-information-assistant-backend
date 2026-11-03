package vn.hcmute.edu.sia.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UploadDocumentRequest(
        @NotNull(message = "Document file must not be null")
        MultipartFile file,

        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,

        Boolean allowDownload
) {}
