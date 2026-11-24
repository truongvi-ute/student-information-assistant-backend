package vn.hcmute.edu.sia.entity;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.MessageSenderType;

@Document(collection = "messages")
@TypeAlias("USER_MESSAGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMessage extends ChatMessage {

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_CLIENT_MESSAGE_ID_LENGTH = 128;

    @Field("sender_user_id")
    private String senderUserId;

    @Field("client_message_id")
    private String clientMessageId;

    public UserMessage(
            String conversationId,
            UUID senderUserId,
            AccountRole senderRole,
            String content
    ) {
        this(conversationId, senderUserId, senderRole, content, null);
    }

    public UserMessage(
            String conversationId,
            UUID senderUserId,
            AccountRole senderRole,
            String content,
            String clientMessageId
    ) {
        super(
                conversationId,
                resolveSenderType(senderUserId, senderRole),
                content,
                MAX_CONTENT_LENGTH
        );
        this.senderUserId = senderUserId.toString();
        this.clientMessageId = normalizeClientMessageId(clientMessageId);
    }

    public static String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return null;
        }
        String normalized = clientMessageId.strip();
        if (normalized.length() > MAX_CLIENT_MESSAGE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Client message ID must not exceed " + MAX_CLIENT_MESSAGE_ID_LENGTH + " characters"
            );
        }
        return normalized;
    }

    private static MessageSenderType resolveSenderType(
            UUID senderUserId,
            AccountRole senderRole
    ) {
        if (senderUserId == null) {
            throw new IllegalArgumentException("Sender ID must not be null");
        }
        if (senderRole == AccountRole.STUDENT) {
            return MessageSenderType.STUDENT;
        }
        if (senderRole == AccountRole.ADVISOR) {
            return MessageSenderType.ADVISOR;
        }
        throw new IllegalArgumentException("Only students and advisors can send user messages");
    }
}
