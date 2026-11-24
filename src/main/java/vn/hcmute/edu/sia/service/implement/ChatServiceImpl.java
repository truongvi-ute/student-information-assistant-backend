package vn.hcmute.edu.sia.service.implement;

import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.request.MarkChatReadRequest;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.dto.response.*;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.AdvisorProfile;
import vn.hcmute.edu.sia.entity.ChatConversation;
import vn.hcmute.edu.sia.entity.ChatMessage;
import vn.hcmute.edu.sia.entity.DirectConversation;
import vn.hcmute.edu.sia.entity.UserMessage;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.CatalogStatus;
import vn.hcmute.edu.sia.enums.MessageSenderType;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.AdvisorProfileRepository;
import vn.hcmute.edu.sia.repository.ChatConversationRepository;
import vn.hcmute.edu.sia.repository.ChatMessageRepository;
import vn.hcmute.edu.sia.repository.DepartmentRepository;
import vn.hcmute.edu.sia.service.ChatService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private static final String USER_QUEUE_MESSAGES = "/queue/messages";
    private static final String USER_QUEUE_READ_RECEIPTS = "/queue/read-receipts";

    private final AccountRepository accountRepository;
    private final AdvisorProfileRepository advisorProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void sendPrivateMessage(
            UUID senderId,
            SendChatMessageRequest request
    ) {
        if (request == null || request.recipientId() == null) {
            throw new IllegalArgumentException("Recipient ID must not be null");
        }

        Account sender = accountRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        Account recipient = accountRepository.findById(request.recipientId())
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        if (sender.getAccessStatus() == AccountAccessStatus.BLOCKED
                || recipient.getAccessStatus() == AccountAccessStatus.BLOCKED) {
            throw new IllegalArgumentException("Blocked accounts cannot send or receive chat messages");
        }

        boolean allowedPair = isAllowedDirectPair(sender.getRole(), recipient.getRole());

        if (!allowedPair) {
            throw new IllegalArgumentException(
                    "Chat is only allowed between advisor and student"
            );
        }

        String content = request.message() == null
                ? ""
                : request.message().trim();

        if (content.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }

        String clientMessageId = UserMessage.normalizeClientMessageId(request.clientMessageId());

        Account student = sender.getRole() == AccountRole.STUDENT
                ? sender
                : recipient;

        Account advisor = sender.getRole() == AccountRole.ADVISOR
                ? sender
                : recipient;

        DirectConversation conversation = getOrCreateConversation(
                student,
                advisor
        );

        if (clientMessageId != null) {
            UserMessage existingMessage = findExistingUserMessage(
                    conversation.getId(),
                    sender.getId().toString(),
                    clientMessageId
            );
            if (existingMessage != null) {
                boolean repaired = updateConversationLastMessage(conversation.getId(), existingMessage, false);
                ChatMessageEvent event = toChatEvent(existingMessage, sender.getId(), recipient.getId());
                if (repaired
                        && existingMessage.getCreatedAt() != null
                        && !existingMessage.getCreatedAt().isAfter(Instant.now().minusSeconds(1))) {
                    sendEventToUser(recipient.getEmail(), USER_QUEUE_MESSAGES, event);
                }
                sendEventToUser(sender.getEmail(), USER_QUEUE_MESSAGES, event);
                return;
            }
        }

        UserMessage message = new UserMessage(
                conversation.getId(),
                sender.getId(),
                sender.getRole(),
                content,
                clientMessageId
        );

        UserMessage saved;
        try {
            saved = messageRepository.save(message);
        } catch (DuplicateKeyException exception) {
            if (clientMessageId != null) {
                UserMessage concurrentExisting = findExistingUserMessage(
                        conversation.getId(),
                        sender.getId().toString(),
                        clientMessageId
                );
                if (concurrentExisting != null) {
                    updateConversationLastMessage(conversation.getId(), concurrentExisting, false);
                    sendEventToUser(
                            sender.getEmail(),
                            USER_QUEUE_MESSAGES,
                            toChatEvent(concurrentExisting, sender.getId(), recipient.getId())
                    );
                    return;
                }
            }
            throw exception;
        }

        updateConversationLastMessage(conversation.getId(), saved, true);

        ChatMessageEvent event = toChatEvent(saved, sender.getId(), recipient.getId());

        sendEventToUser(recipient.getEmail(), USER_QUEUE_MESSAGES, event);
        sendEventToUser(sender.getEmail(), USER_QUEUE_MESSAGES, event);
    }

    @Override
    public List<ChatContactResponse> getContacts(UUID currentUserId) {
        Account currentUser = accountRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        AccountRole contactRole = switch (currentUser.getRole()) {
            case ADVISOR -> AccountRole.STUDENT;
            case STUDENT -> AccountRole.ADVISOR;
            default -> throw new IllegalArgumentException(
                    "Only advisors and students can use chat"
            );
        };

        if (contactRole == AccountRole.ADVISOR) {
            return advisorProfileRepository
                    .findAllWithAccountAndDepartmentByAccountAccessStatus(
                            AccountRole.ADVISOR,
                            AccountAccessStatus.ACTIVE
                    )
                    .stream()
                    .map(profile -> new ChatContactResponse(
                            profile.getAccount().getId(),
                            profile.getAccount().getFullName(),
                            profile.getAccount().getRole(),
                            profile.getDepartment().getId(),
                            profile.getDepartment().getName()
                    ))
                    .toList();
        }

        return accountRepository.findAllByRole(contactRole)
                .stream()
                .map(account -> new ChatContactResponse(
                        account.getId(),
                        account.getFullName(),
                        account.getRole(),
                        null,
                        null
                ))
                .toList();
    }

    @Override
    public List<DepartmentResponse> getDepartments() {
        return departmentRepository.findAllByStatus(CatalogStatus.ACTIVE)
                .stream()
                .map(d -> new DepartmentResponse(d.getId(), d.getName()))
                .toList();
    }

    @Override
    public List<ChatConversationResponse> getConversations(UUID currentUserId) {
        Account currentUser = accountRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));

        List<DirectConversation> rawConversations;
        if (currentUser.getRole() == AccountRole.STUDENT) {
            rawConversations = conversationRepository
                    .findByStudentIdAndConversationType(currentUserId.toString(), "ADVISOR");
        } else if (currentUser.getRole() == AccountRole.ADVISOR) {
            rawConversations = conversationRepository
                    .findByAdvisorIdAndConversationType(currentUserId.toString(), "ADVISOR");
        } else {
            return List.of();
        }

        List<DirectConversation> activeConversations = rawConversations.stream()
                .filter(c -> c.getDeletedAt() == null)
                .toList();

        if (activeConversations.isEmpty()) {
            return List.of();
        }

        Set<UUID> partnerIds = new HashSet<>();
        for (DirectConversation c : activeConversations) {
            String partnerIdStr = currentUser.getRole() == AccountRole.STUDENT
                    ? c.getAdvisorId()
                    : c.getStudentId();
            try {
                partnerIds.add(UUID.fromString(partnerIdStr));
            } catch (Exception ignored) {}
        }

        if (partnerIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Account> accountMap = accountRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        Map<UUID, AdvisorProfile> advisorMap = Collections.emptyMap();
        if (currentUser.getRole() == AccountRole.STUDENT) {
            advisorMap = advisorProfileRepository
                    .findAllWithAccountAndDepartmentByAccountIdIn(
                            partnerIds,
                            AccountRole.ADVISOR
                    ).stream()
                    .collect(Collectors.toMap(
                            profile -> profile.getAccount().getId(),
                            Function.identity()
                    ));
        }

        Map<String, Long> unreadCounts = computeUnreadCounts(activeConversations, currentUser.getRole());

        List<ChatConversationResponse> result = new ArrayList<>();
        for (DirectConversation conv : activeConversations) {
            UUID partnerId;
            try {
                partnerId = currentUser.getRole() == AccountRole.STUDENT
                        ? UUID.fromString(conv.getAdvisorId())
                        : UUID.fromString(conv.getStudentId());
            } catch (Exception e) {
                continue;
            }

            Account partner = accountMap.get(partnerId);
            if (partner == null) {
                continue;
            }

            String departmentName = null;
            if (advisorMap.containsKey(partnerId)) {
                AdvisorProfile advisor = advisorMap.get(partnerId);
                if (advisor.getDepartment() != null) {
                    departmentName = advisor.getDepartment().getName();
                }
            }

            String lastMsg = conv.getLastMessage();
            Instant lastMsgAt = conv.getLastMessageAt();

            if (lastMsg == null) {
                var latestMsgOpt = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conv.getId());
                if (latestMsgOpt.isPresent()) {
                    var latest = latestMsgOpt.get();
                    lastMsg = latest.getContent();
                    lastMsgAt = latest.getCreatedAt();
                } else {
                    lastMsgAt = conv.getCreatedAt();
                }
            }

            Instant partnerLastReadAt = currentUser.getRole() == AccountRole.STUDENT
                    ? conv.getAdvisorLastReadAt()
                    : conv.getStudentLastReadAt();
            long unreadCount = unreadCounts.getOrDefault(conv.getId(), 0L);

            result.add(new ChatConversationResponse(
                    conv.getId(),
                    partner.getId(),
                    partner.getFullName(),
                    partner.getRole(),
                    departmentName,
                    lastMsg != null ? lastMsg : "",
                    lastMsgAt != null ? lastMsgAt : conv.getCreatedAt(),
                    unreadCount,
                    partnerLastReadAt
            ));
        }

        result.sort((a, b) -> {
            Instant t1 = a.lastMessageAt();
            Instant t2 = b.lastMessageAt();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        return result;
    }

    @Override
    public MarkChatReadResponse markConversationAsRead(
            UUID currentUserId,
            MarkChatReadRequest request
    ) {
        if (request == null || request.contactId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact ID is required");
        }

        Account currentUser = accountRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));

        Account contact = accountRepository.findById(request.contactId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact not found"));

        if (!isAllowedDirectPair(currentUser.getRole(), contact.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chat is only allowed between advisor and student"
            );
        }

        UUID studentId = currentUser.getRole() == AccountRole.STUDENT
                ? currentUserId
                : request.contactId();
        UUID advisorId = currentUser.getRole() == AccountRole.ADVISOR
                ? currentUserId
                : request.contactId();

        var conversationOpt = conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(),
                advisorId.toString(),
                "ADVISOR"
        );

        if (conversationOpt.isEmpty() || conversationOpt.get().getDeletedAt() != null) {
            return new MarkChatReadResponse(null, request.contactId(), null, false);
        }

        DirectConversation conv = conversationOpt.get();
        Instant targetReadAt = resolveTargetReadAt(conv, request);
        if (targetReadAt == null) {
            return new MarkChatReadResponse(conv.getId(), request.contactId(), null, false);
        }

        boolean isStudent = currentUser.getRole() == AccountRole.STUDENT;
        String readField = isStudent ? "student_last_read_at" : "advisor_last_read_at";

        Query query = new Query(
                Criteria.where("_id").is(conv.getId())
                        .and("deleted_at").isNull()
        );
        Update update = new Update().max(readField, targetReadAt);
        DirectConversation beforeUpdate = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(false),
                DirectConversation.class
        );

        if (beforeUpdate == null) {
            return new MarkChatReadResponse(null, request.contactId(), null, false);
        }

        Instant beforeReadAt = isStudent
                ? beforeUpdate.getStudentLastReadAt()
                : beforeUpdate.getAdvisorLastReadAt();
        boolean updated = beforeReadAt == null || targetReadAt.isAfter(beforeReadAt);
        Instant effectiveReadAt = updated ? targetReadAt : beforeReadAt;

        if (updated) {
            ChatReadReceiptEvent receiptEvent = ChatReadReceiptEvent.of(
                    conv.getId(),
                    currentUser.getId().toString(),
                    contact.getId().toString(),
                    effectiveReadAt
            );
            sendEventToUser(contact.getEmail(), USER_QUEUE_READ_RECEIPTS, receiptEvent);
            sendEventToUser(currentUser.getEmail(), USER_QUEUE_READ_RECEIPTS, receiptEvent);
        }

        return new MarkChatReadResponse(conv.getId(), request.contactId(), effectiveReadAt, updated);
    }

    @Override
    public ChatHistoryResponse getHistory(
            UUID currentUserId,
            UUID contactId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0; size must be between 1 and 100"
            );
        }

        Account currentUser = accountRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Account not found"
                ));

        Account contact = accountRepository.findById(contactId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Contact not found"
                ));

        boolean allowedPair = isAllowedDirectPair(currentUser.getRole(), contact.getRole());

        if (!allowedPair) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chat is only allowed between advisor and student"
            );
        }

        UUID studentId = currentUser.getRole() == AccountRole.STUDENT
                ? currentUserId : contactId;

        UUID advisorId = currentUser.getRole() == AccountRole.ADVISOR
                ? currentUserId : contactId;

        var conversation = conversationRepository
                .findByStudentIdAndAdvisorIdAndConversationType(studentId.toString(), advisorId.toString(), "ADVISOR");

        if (conversation.isEmpty() || conversation.get().getDeletedAt() != null) {
            return new ChatHistoryResponse(null, List.of(), false, null, null);
        }

        DirectConversation conv = conversation.get();
        boolean isStudent = currentUser.getRole() == AccountRole.STUDENT;
        Instant myLastReadAt = isStudent ? conv.getStudentLastReadAt() : conv.getAdvisorLastReadAt();
        Instant partnerLastReadAt = isStudent ? conv.getAdvisorLastReadAt() : conv.getStudentLastReadAt();

        var slice = messageRepository
                .findByConversationIdOrderByCreatedAtDescIdDesc(
                        conv.getId(),
                        PageRequest.of(page, size)
                );

        List<ChatMessageResponse> messages = new ArrayList<>(
                slice.getContent().stream()
                        .filter(msg -> msg.getDeletedAt() == null)
                        .filter(UserMessage.class::isInstance)
                        .map(UserMessage.class::cast)
                        .map(message -> {
                            String senderId = message.getSenderUserId();

                            String recipientId = senderId.equals(currentUserId.toString())
                                    ? contactId.toString()
                                    : currentUserId.toString();

                            return new ChatMessageResponse(
                                    message.getId(),
                                    conv.getId(),
                                    message.getClientMessageId(),
                                    senderId,
                                    recipientId,
                                    message.getContent(),
                                    message.getCreatedAt()
                            );
                        })
                        .toList()
        );

        Collections.reverse(messages);

        return new ChatHistoryResponse(
                conv.getId(),
                messages,
                slice.hasNext(),
                partnerLastReadAt,
                myLastReadAt
        );
    }

    private Instant resolveTargetReadAt(DirectConversation conv, MarkChatReadRequest request) {
        if (request.lastMessageId() != null && !request.lastMessageId().isBlank()) {
            ChatMessage targetMsg = messageRepository.findById(request.lastMessageId().strip())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message not found"));
            if (!conv.getId().equals(targetMsg.getConversationId()) || targetMsg.getDeletedAt() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Message does not belong to this conversation"
                );
            }
            return targetMsg.getCreatedAt() != null
                    ? targetMsg.getCreatedAt().truncatedTo(ChronoUnit.MILLIS)
                    : null;
        }

        if (request.lastReadAt() != null) {
            if (conv.getLastMessageAt() == null) {
                return null;
            }
            Instant truncatedRequest = request.lastReadAt().truncatedTo(ChronoUnit.MILLIS);
            Instant truncatedLast = conv.getLastMessageAt().truncatedTo(ChronoUnit.MILLIS);
            return truncatedRequest.isAfter(truncatedLast) ? truncatedLast : truncatedRequest;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Either lastMessageId or lastReadAt is required"
        );
    }

    private Map<String, Long> computeUnreadCounts(
            List<DirectConversation> conversations,
            AccountRole currentUserRole
    ) {
        MessageSenderType partnerSenderType = currentUserRole == AccountRole.STUDENT
                ? MessageSenderType.ADVISOR
                : MessageSenderType.STUDENT;

        List<Criteria> conversationCriteriaList = new ArrayList<>();
        for (DirectConversation conv : conversations) {
            Instant myLastReadAt = currentUserRole == AccountRole.STUDENT
                    ? conv.getStudentLastReadAt()
                    : conv.getAdvisorLastReadAt();

            if (myLastReadAt != null
                    && conv.getLastMessageAt() != null
                    && !conv.getLastMessageAt().isAfter(myLastReadAt)) {
                continue;
            }

            if (myLastReadAt == null) {
                conversationCriteriaList.add(Criteria.where("conversation_id").is(conv.getId()));
            } else {
                conversationCriteriaList.add(
                        Criteria.where("conversation_id").is(conv.getId())
                                .and("created_at").gt(myLastReadAt)
                );
            }
        }

        if (conversationCriteriaList.isEmpty()) {
            return Collections.emptyMap();
        }

        Criteria matchCriteria = new Criteria().andOperator(
                Criteria.where("deleted_at").isNull(),
                Criteria.where("sender_type").is(partnerSenderType.name()),
                new Criteria().orOperator(conversationCriteriaList.toArray(Criteria[]::new))
        );

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.group("conversation_id").count().as("unreadCount")
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, "messages", Document.class);
        if (results == null) {
            return Collections.emptyMap();
        }

        Map<String, Long> unreadMap = new HashMap<>();
        for (Document doc : results.getMappedResults()) {
            Object idObj = doc.get("_id");
            Object countObj = doc.get("unreadCount");
            if (idObj != null && countObj instanceof Number number) {
                unreadMap.put(idObj.toString(), number.longValue());
            }
        }
        return unreadMap;
    }

    private boolean updateConversationLastMessage(
            String conversationId,
            UserMessage message,
            boolean allowEqualTimestamp
    ) {
        Criteria timestampCriteria = allowEqualTimestamp
                ? Criteria.where("last_message_at").lte(message.getCreatedAt())
                : Criteria.where("last_message_at").lt(message.getCreatedAt());

        Query updateQuery = new Query(
                Criteria.where("_id").is(conversationId)
                        .orOperator(
                                Criteria.where("last_message_at").isNull(),
                                timestampCriteria
                        )
        );
        Update update = new Update()
                .set("last_message", message.getContent())
                .set("last_message_at", message.getCreatedAt())
                .set("updated_at", message.getCreatedAt());
        var result = mongoTemplate.updateFirst(updateQuery, update, ChatConversation.class);
        return result != null && result.getModifiedCount() > 0;
    }

    private UserMessage findExistingUserMessage(
            String conversationId,
            String senderUserId,
            String clientMessageId
    ) {
        Query query = new Query(
                Criteria.where("conversation_id").is(conversationId)
                        .and("sender_user_id").is(senderUserId)
                        .and("client_message_id").is(clientMessageId)
        );
        return mongoTemplate.findOne(query, UserMessage.class);
    }

    private ChatMessageEvent toChatEvent(UserMessage saved, UUID senderId, UUID recipientId) {
        return ChatMessageEvent.chat(
                saved.getId(),
                saved.getConversationId(),
                saved.getClientMessageId(),
                senderId.toString(),
                recipientId.toString(),
                saved.getContent(),
                saved.getCreatedAt()
        );
    }

    private void sendEventToUser(String userEmail, String destination, Object payload) {
        if (userEmail == null || userEmail.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(userEmail, destination, payload);
    }

    private static boolean isAllowedDirectPair(AccountRole role1, AccountRole role2) {
        return (role1 == AccountRole.ADVISOR && role2 == AccountRole.STUDENT)
                || (role1 == AccountRole.STUDENT && role2 == AccountRole.ADVISOR);
    }

    private DirectConversation getOrCreateConversation(
            Account student,
            Account advisor
    ) {
        String studentId = student.getId().toString();
        String advisorId = advisor.getId().toString();

        var existing = conversationRepository
                .findByStudentIdAndAdvisorIdAndConversationType(
                        studentId,
                        advisorId,
                        "ADVISOR"
                );

        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return conversationRepository.insert(
                    new DirectConversation(
                            student.getId(),
                            advisor.getId()
                    )
            );
        } catch (DuplicateKeyException exception) {
            return conversationRepository
                    .findByStudentIdAndAdvisorIdAndConversationType(
                            studentId,
                            advisorId,
                            "ADVISOR"
                    )
                    .orElseThrow(() -> exception);
        }
    }
}
