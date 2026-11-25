package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "conversations")
@TypeAlias("DIRECT_CONVERSATION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectConversation extends ChatConversation {

    @Field("advisor_id")
    private String advisorId;

    @Field("student_last_read_at")
    private Instant studentLastReadAt;

    @Field("advisor_last_read_at")
    private Instant advisorLastReadAt;

    public DirectConversation(UUID studentId, UUID advisorId) {
        super(studentId, "ADVISOR");
        if (advisorId == null) {
            throw new IllegalArgumentException("Advisor ID must not be null");
        }
        if (studentId.equals(advisorId)) {
            throw new IllegalArgumentException("Student and advisor must be different accounts");
        }
        this.advisorId = advisorId.toString();
    }
}
