package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Document(collection = "conversations")
@Getter
@CompoundIndex(
        name = "uq_advisor_conversation_pair",
        def = "{'student_id': 1, 'advisor_id': 1}",
        unique = true,
        partialFilter = "{'conversation_type': 'ADVISOR'}"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatConversation {
    @Id
    private String id;

    @Field("student_id")
    private String studentId;

    @Field("advisor_id")
    private String advisorId;

    @Field("conversation_type")
    private String conversationType;

    private String title;

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

    public ChatConversation(UUID studentId, UUID advisorId) {
        if (studentId == null) {
            throw new IllegalArgumentException("Student ID must not be null");
        }

        if (advisorId == null) {
            throw new IllegalArgumentException("Advisor ID must not be null");
        }

        if (studentId.equals(advisorId)) {
            throw new IllegalArgumentException("Student and advisor must be different accounts");
        }

        Instant now = Instant.now();

        this.id = UUID.randomUUID().toString();
        this.studentId = studentId.toString();
        this.advisorId = advisorId.toString();
        this.conversationType = "ADVISOR";
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }
}