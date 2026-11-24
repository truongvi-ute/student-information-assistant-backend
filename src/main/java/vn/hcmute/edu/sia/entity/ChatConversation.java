package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "conversations")
@Getter
@CompoundIndex(
        name = "uq_advisor_conversation_pair",
        def = "{'student_id': 1, 'advisor_id': 1}",
        unique = true,
        partialFilter = "{'conversation_type': 'ADVISOR'}"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ChatConversation {

    @Id
    private String id;

    @Field("student_id")
    private String studentId;

    @Field("conversation_type")
    private String conversationType;

    private String status;

    @Field("last_message")
    private String lastMessage;

    @Field("last_message_at")
    private Instant lastMessageAt;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("deleted_at")
    private Instant deletedAt;

    protected ChatConversation(UUID studentId, String conversationType) {
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID must not be null");
        }
        if (conversationType == null || conversationType.isBlank()) {
            throw new IllegalArgumentException("Conversation type must not be blank");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        this.id = UUID.randomUUID().toString();
        this.studentId = studentId.toString();
        this.conversationType = conversationType.strip();
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void softDelete() {
        if (this.deletedAt == null) {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            this.deletedAt = now;
            this.updatedAt = now;
            this.status = "CLOSED";
        }
    }
}
