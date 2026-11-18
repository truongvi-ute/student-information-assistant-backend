package vn.hcmute.edu.sia.service.implement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatResponse;
import vn.hcmute.edu.sia.dto.internal.AiServiceSourceCitation;
import vn.hcmute.edu.sia.dto.request.CreateAiConversationRequest;
import vn.hcmute.edu.sia.dto.request.SendAiMessageRequest;
import vn.hcmute.edu.sia.dto.response.AiChatbotHistoryResponse;
import vn.hcmute.edu.sia.dto.response.AiChatbotTurnResponse;
import vn.hcmute.edu.sia.dto.response.AiCitationResponse;
import vn.hcmute.edu.sia.dto.response.AiConversationResponse;
import vn.hcmute.edu.sia.dto.response.AiMessageResponse;
import vn.hcmute.edu.sia.entity.AIConversation;
import vn.hcmute.edu.sia.entity.AIMessage;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.ChatMessage;
import vn.hcmute.edu.sia.entity.Citation;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.entity.UserMessage;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.ChatConversationRepository;
import vn.hcmute.edu.sia.repository.ChatMessageRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.AiChatbotService;
import vn.hcmute.edu.sia.service.AiServiceClient;

@Service
@RequiredArgsConstructor
public class AiChatbotServiceImpl implements AiChatbotService {

    private static final String AI_CONVERSATION_TYPE = "AI";
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_AUTO_TITLE_LENGTH = 60;
    private static final int MAX_PREVIEW_MESSAGE_LENGTH = 120;

    private final AccountRepository accountRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final AiServiceClient aiServiceClient;
    private final MongoTemplate mongoTemplate;

    @Override
    public AiConversationResponse createConversation(
            UUID studentId,
            CreateAiConversationRequest request
    ) {
        requireActiveStudent(studentId);

        String title = request != null ? request.title() : null;
        UUID scopeMajorId = request != null ? request.scopeMajorId() : null;

        AIConversation conversation = new AIConversation(studentId, title, scopeMajorId);
        AIConversation saved = conversationRepository.save(conversation);

        return toConversationResponse(saved);
    }

    @Override
    public List<AiConversationResponse> getConversations(UUID studentId) {
        requireActiveStudent(studentId);

        return conversationRepository
                .findByStudentIdAndConversationTypeAndDeletedAtIsNullOrderByLastMessageAtDescCreatedAtDesc(
                        studentId.toString(),
                        AI_CONVERSATION_TYPE
                )
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Override
    public AiChatbotHistoryResponse getMessages(
            UUID studentId,
            String conversationId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0; size must be between 1 and 100"
            );
        }

        requireActiveStudent(studentId);
        AIConversation conversation = requireOwnedConversation(studentId, conversationId);

        Slice<ChatMessage> slice = messageRepository
                .findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        conversation.getId(),
                        PageRequest.of(page, size)
                );

        List<ChatMessage> rawMessages = slice.getContent();
        Map<UUID, Document> documentsById = loadDocumentsForMessages(rawMessages);

        List<AiMessageResponse> messages = new ArrayList<>(
                rawMessages.stream()
                        .map(msg -> toMessageResponse(msg, documentsById))
                        .toList()
        );
        Collections.reverse(messages);

        return new AiChatbotHistoryResponse(messages, slice.hasNext());
    }

    @Override
    public AiChatbotTurnResponse askQuestion(
            UUID studentId,
            String conversationId,
            SendAiMessageRequest request
    ) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must not be blank");
        }

        int topK = request.topK() != null ? request.topK() : DEFAULT_TOP_K;
        if (topK < 1 || topK > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "topK must be between 1 and 20");
        }

        requireActiveStudent(studentId);
        AIConversation conversation = requireOwnedConversation(studentId, conversationId);

        String question = request.question().strip();
        String filenameFilter = request.filenameFilter() != null && !request.filenameFilter().isBlank()
                ? request.filenameFilter().strip()
                : null;

        UserMessage studentMessage = new UserMessage(
                conversation.getId(),
                studentId,
                AccountRole.STUDENT,
                question
        );

        AiServiceChatResponse aiResponse = aiServiceClient.chat(
                new AiServiceChatRequest(question, topK, filenameFilter)
        );

        Map<UUID, Document> documentsById = new HashMap<>();
        List<Citation> citations = mapCitations(aiResponse.sources(), documentsById);
        AIMessage aiMessage = new AIMessage(
                conversation.getId(),
                aiResponse.answer(),
                citations,
                null
        );

        messageRepository.saveAll(List.of(studentMessage, aiMessage));

        if (AIConversation.DEFAULT_TITLE.equals(conversation.getTitle())) {
            conversation.updateTitle(truncateText(question, MAX_AUTO_TITLE_LENGTH));
        }

        String previewMessage = truncateText(aiMessage.getContent(), MAX_PREVIEW_MESSAGE_LENGTH);
        Query updateQuery = new Query(Criteria.where("_id").is(conversation.getId()));
        Update update = new Update()
                .set("title", conversation.getTitle())
                .set("last_message", previewMessage)
                .set("last_message_at", aiMessage.getCreatedAt())
                .set("updated_at", aiMessage.getCreatedAt());
        mongoTemplate.updateFirst(updateQuery, update, AIConversation.class);

        return new AiChatbotTurnResponse(
                conversation.getId(),
                conversation.getTitle(),
                toMessageResponse(studentMessage, documentsById),
                toMessageResponse(aiMessage, documentsById)
        );
    }

    @Override
    public void deleteConversation(UUID studentId, String conversationId) {
        requireActiveStudent(studentId);
        AIConversation conversation = requireOwnedConversation(studentId, conversationId);

        conversation.softDelete();
        conversationRepository.save(conversation);
    }

    private Account requireActiveStudent(UUID studentId) {
        Account account = accountRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Account not found"
                ));

        if (account.getRole() != AccountRole.STUDENT
                || account.getAccessStatus() != AccountAccessStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only active student accounts can use AI chatbot"
            );
        }

        return account;
    }

    private AIConversation requireOwnedConversation(UUID studentId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Conversation ID must not be blank"
            );
        }

        return conversationRepository
                .findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                        conversationId.strip(),
                        studentId.toString(),
                        AI_CONVERSATION_TYPE
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "AI conversation not found"
                ));
    }

    private List<Citation> mapCitations(
            List<AiServiceSourceCitation> sources,
            Map<UUID, Document> documentsById
    ) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        Set<UUID> directDocumentIds = sources.stream()
                .filter(Objects::nonNull)
                .map(source -> parseUuidOrNull(source.documentId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!directDocumentIds.isEmpty()) {
            for (Document doc : documentRepository.findAllById(directDocumentIds)) {
                documentsById.put(doc.getId(), doc);
            }
        }

        Set<String> fallbackFilenames = sources.stream()
                .filter(Objects::nonNull)
                .filter(source -> parseUuidOrNull(source.documentId()) == null)
                .map(AiServiceSourceCitation::filename)
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .collect(Collectors.toSet());

        Map<String, Document> documentByFilename = new HashMap<>();
        if (!fallbackFilenames.isEmpty()) {
            for (Document doc : documentRepository.findByOriginalFilenameIn(fallbackFilenames)) {
                documentByFilename.putIfAbsent(doc.getOriginalFilename(), doc);
                documentsById.putIfAbsent(doc.getId(), doc);
            }
        }

        List<Citation> citations = new ArrayList<>();
        for (AiServiceSourceCitation source : sources) {
            if (source == null || source.filename() == null || source.filename().isBlank()) {
                continue;
            }
            String filename = source.filename().strip();
            UUID resolvedDocumentId = parseUuidOrNull(source.documentId());
            Document matchedDocument = resolvedDocumentId != null
                    ? documentsById.get(resolvedDocumentId)
                    : documentByFilename.get(filename);

            if (resolvedDocumentId == null && matchedDocument != null) {
                resolvedDocumentId = matchedDocument.getId();
            }

            String displayTitle = matchedDocument != null
                    && matchedDocument.getTitle() != null
                    && !matchedDocument.getTitle().isBlank()
                    ? matchedDocument.getTitle()
                    : filename;

            Integer pageNumber = source.pageNumber() != null && source.pageNumber() > 0
                    ? source.pageNumber()
                    : null;

            String quotedContent = source.content() != null && !source.content().isBlank()
                    ? source.content()
                    : source.snippet();

            citations.add(new Citation(
                    resolvedDocumentId,
                    displayTitle,
                    pageNumber,
                    "chunk_" + source.chunkIndex(),
                    quotedContent,
                    source.score(),
                    source.charStart(),
                    source.charEnd()
            ));
        }

        return citations;
    }

    private Map<UUID, Document> loadDocumentsForMessages(List<ChatMessage> messages) {
        Set<UUID> documentIds = messages.stream()
                .filter(AIMessage.class::isInstance)
                .map(AIMessage.class::cast)
                .flatMap(aiMessage -> aiMessage.getCitations().stream())
                .map(citation -> parseUuidOrNull(citation.getDocumentId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Document> documentsById = new HashMap<>();
        if (!documentIds.isEmpty()) {
            for (Document doc : documentRepository.findAllById(documentIds)) {
                documentsById.put(doc.getId(), doc);
            }
        }
        return documentsById;
    }

    private UUID parseUuidOrNull(String rawUuid) {
        if (rawUuid == null || rawUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid.strip());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private AiConversationResponse toConversationResponse(AIConversation conversation) {
        UUID scopeMajorId = null;
        if (conversation.getScopeMajorId() != null) {
            try {
                scopeMajorId = UUID.fromString(conversation.getScopeMajorId());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new AiConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                scopeMajorId,
                conversation.getLastMessage() != null ? conversation.getLastMessage() : "",
                conversation.getLastMessageAt() != null
                        ? conversation.getLastMessageAt()
                        : conversation.getCreatedAt(),
                conversation.getCreatedAt()
        );
    }

    private AiMessageResponse toMessageResponse(
            ChatMessage message,
            Map<UUID, Document> documentsById
    ) {
        List<AiCitationResponse> citations = switch (message) {
            case AIMessage aiMessage -> aiMessage.getCitations().stream()
                    .map(citation -> toCitationResponse(citation, documentsById))
                    .toList();
            case UserMessage ignored -> List.of();
            default -> List.of();
        };

        return new AiMessageResponse(
                message.getId(),
                message.getSenderType(),
                message.getContent(),
                citations,
                message.getCreatedAt()
        );
    }

    private AiCitationResponse toCitationResponse(
            Citation citation,
            Map<UUID, Document> documentsById
    ) {
        UUID documentId = parseUuidOrNull(citation.getDocumentId());
        Document matchedDocument = documentId != null ? documentsById.get(documentId) : null;

        String sourceTitle = matchedDocument != null
                && matchedDocument.getTitle() != null
                && !matchedDocument.getTitle().isBlank()
                ? matchedDocument.getTitle()
                : citation.getSourceTitle();
        String originalFilename = matchedDocument != null
                ? matchedDocument.getOriginalFilename()
                : citation.getSourceTitle();

        return new AiCitationResponse(
                documentId,
                sourceTitle,
                citation.getPageNumber(),
                citation.getChunkReference(),
                citation.getQuotedContent(),
                citation.getRelevanceScore(),
                citation.getCharStart(),
                citation.getCharEnd(),
                originalFilename
        );
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength).strip() + "...";
    }
}
