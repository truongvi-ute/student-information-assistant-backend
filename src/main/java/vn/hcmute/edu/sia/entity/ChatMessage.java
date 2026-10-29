package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.hcmute.edu.sia.enums.AccountRole;

@Document(collection = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    @Id
    private String id;

    @Field("conversation_id")
    private String conversationId;

    @Field("sender_user_id")
    private String senderUserId;

    @Field("sender_type")
    private String senderType;

    private String content;

    @Field("message_type")
    private String messageType;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("deleted_at")
    private Instant deletedAt;

    public ChatMessage(
            String conversationId,
            UUID senderUserId,
            AccountRole senderRole,
            String content
    ) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation ID must not be blank");
        }

        if (senderUserId == null) {
            throw new IllegalArgumentException("Sender ID must not be null");
        }

        if (senderRole != AccountRole.STUDENT
                && senderRole != AccountRole.ADVISOR) {
            throw new IllegalArgumentException("Only students and advisors can send direct messages");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }

        String normalizedContent = content.strip();

        if (normalizedContent.codePointCount(
                0, normalizedContent.length()) > 2000) {
            throw new IllegalArgumentException("Message content must not exceed 2000 characters");
        }

        Instant now = Instant.now();

        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId.strip();
        this.senderUserId = senderUserId.toString();
        this.senderType = senderRole.name();
        this.content = normalizedContent;
        this.messageType = "TEXT";
        this.createdAt = now;
        this.updatedAt = now;
    }
}