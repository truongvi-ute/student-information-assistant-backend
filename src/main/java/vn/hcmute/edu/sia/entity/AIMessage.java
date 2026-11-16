package vn.hcmute.edu.sia.entity;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.hcmute.edu.sia.enums.MessageSenderType;

@Document(collection = "messages")
@TypeAlias("AI_MESSAGE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AIMessage extends ChatMessage {

    public static final int MAX_CONTENT_LENGTH = 10000;

    @Field("citations")
    private List<Citation> citations;

    @Field("model_name")
    private String modelName;

    public AIMessage(
            String conversationId,
            String content,
            List<Citation> citations
    ) {
        this(conversationId, content, citations, null);
    }

    public AIMessage(
            String conversationId,
            String content,
            List<Citation> citations,
            String modelName
    ) {
        super(conversationId, MessageSenderType.AI, content, MAX_CONTENT_LENGTH);
        if (citations == null || citations.isEmpty()) {
            this.citations = List.of();
        } else {
            if (citations.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Citations must not contain null elements");
            }
            this.citations = List.copyOf(citations);
        }
        this.modelName = modelName != null && !modelName.isBlank()
                ? modelName.strip()
                : null;
    }
}
