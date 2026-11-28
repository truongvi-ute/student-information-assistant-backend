package vn.hcmute.edu.sia.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDocumentUpdateRequest {

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    // Distinguishes omitted "description" key (400) from explicit null/blank (clear description).
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    @AssertTrue(message = "Description field must be provided")
    private boolean descriptionPresent;

    @NotNull(message = "allowDownload must not be null")
    private Boolean allowDownload;

    @JsonCreator
    public CreateDocumentUpdateRequest() {
    }

    public CreateDocumentUpdateRequest(String title, String description, Boolean allowDownload) {
        this.title = title;
        this.description = description;
        this.descriptionPresent = true;
        this.allowDownload = allowDownload;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }
}
