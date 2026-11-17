package vn.hcmute.edu.sia.entity;

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
