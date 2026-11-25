package vn.hcmute.edu.sia.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.hcmute.edu.sia.enums.MessageSenderType;

@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(
                name = "uq_direct_msg_client_id",
                def = "{'conversation_id': 1, 'sender_user_id': 1, 'client_message_id': 1}",
                unique = true,
                partialFilter = "{'client_message_id': {'$type': 'string'}, 'sender_user_id': {'$type': 'string'}}"
        ),
        @CompoundIndex(
                name = "idx_msg_conv_sender_created",
                def = "{'conversation_id': 1, 'sender_type': 1, 'created_at': -1}"
        ),
        @CompoundIndex(
                name = "idx_msg_conv_created",
                def = "{'conversation_id': 1, 'created_at': -1, '_id': -1}"
        )
})
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

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

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
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            this.deletedAt = now;
            this.updatedAt = now;
        }
    }
}
