package vn.hcmute.edu.sia.entity;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Citation {

    @Field("document_id")
    private String documentId;

    @Field("source_title")
    private String sourceTitle;

    @Field("page_number")
    private Integer pageNumber;

    @Field("chunk_reference")
    private String chunkReference;

    @Field("quoted_content")
    private String quotedContent;

    @Field("relevance_score")
    private Double relevanceScore;

    public Citation(
            UUID documentId,
            String sourceTitle,
            Integer pageNumber,
            String chunkReference,
            String quotedContent,
            Double relevanceScore
    ) {
        if (sourceTitle == null || sourceTitle.isBlank()) {
            throw new IllegalArgumentException("Source title must not be blank");
        }
        if (pageNumber != null && pageNumber <= 0) {
            throw new IllegalArgumentException("Page number must be positive");
        }

        this.documentId = documentId != null ? documentId.toString() : null;
        this.sourceTitle = sourceTitle.strip();
        this.pageNumber = pageNumber;
        this.chunkReference = chunkReference != null && !chunkReference.isBlank()
                ? chunkReference.strip()
                : null;
        this.quotedContent = quotedContent != null ? quotedContent.strip() : "";
        this.relevanceScore = relevanceScore;
    }
}
