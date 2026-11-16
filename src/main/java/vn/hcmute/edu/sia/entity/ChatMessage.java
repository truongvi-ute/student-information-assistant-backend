package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.hcmute.edu.sia.enums.MessageSenderType;

@Document(collection = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ChatMessage {

    @Id
    private String id;

    @Field("conversation_id")
    private String conversationId;

    @Field("sender_type")
    private MessageSenderType senderType;

    private String content;

    @Field("message_type")
    private String messageType;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("deleted_at")
    private Instant deletedAt;

    protected ChatMessage(
            String conversationId,
            MessageSenderType senderType,
            String content,
            int maxContentLength
    ) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation ID must not be blank");
        }
        if (senderType == null) {
            throw new IllegalArgumentException("Sender type must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }

        String normalizedContent = content.strip();
        if (normalizedContent.codePointCount(0, normalizedContent.length()) > maxContentLength) {
            throw new IllegalArgumentException(
                    "Message content must not exceed " + maxContentLength + " characters"
            );
        }

        Instant now = Instant.now();

        this.id = UUID.randomUUID().toString();
        this.conversationId = conversationId.strip();
        this.senderType = senderType;
        this.content = normalizedContent;
        this.messageType = "TEXT";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void deleteMessage() {
        if (this.deletedAt == null) {
            Instant now = Instant.now();
            this.deletedAt = now;
            this.updatedAt = now;
        }
    }
}
