package vn.hcmute.edu.sia.entity;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "conversations")
@TypeAlias("AI_CONVERSATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AIConversation extends ChatConversation {

    public static final String DEFAULT_TITLE = "Cuộc trò chuyện mới";
    private static final int MAX_TITLE_LENGTH = 150;

    private String title;

    @Field("scope_major_id")
    private String scopeMajorId;

    public AIConversation(UUID studentId, String title, UUID scopeMajorId) {
        super(studentId, "AI");
        this.title = normalizeTitle(title);
        this.scopeMajorId = scopeMajorId != null ? scopeMajorId.toString() : null;
    }

    public void updateTitle(String title) {
        this.title = normalizeTitle(title);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String trimmed = title.strip();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            return trimmed.substring(0, MAX_TITLE_LENGTH);
        }
        return trimmed;
    }
}
