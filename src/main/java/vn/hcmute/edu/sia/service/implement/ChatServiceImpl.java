package vn.hcmute.edu.sia.service.implement;

import org.springframework.dao.DuplicateKeyException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.dto.response.*;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.AdvisorAccount;
import vn.hcmute.edu.sia.entity.ChatConversation;
import vn.hcmute.edu.sia.entity.ChatMessage;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.CatalogStatus;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.AdvisorAccountRepository;
import vn.hcmute.edu.sia.repository.ChatConversationRepository;
import vn.hcmute.edu.sia.repository.ChatMessageRepository;
import vn.hcmute.edu.sia.repository.DepartmentRepository;
import vn.hcmute.edu.sia.service.ChatService;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final AccountRepository accountRepository;
    private final AdvisorAccountRepository advisorAccountRepository;
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
        Account sender = accountRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found"));

        Account recipient = accountRepository.findById(request.recipientId())
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        boolean allowedPair =
                (sender.getRole() == AccountRole.ADVISOR
                        && recipient.getRole() == AccountRole.STUDENT)
                        || (sender.getRole() == AccountRole.STUDENT
                        && recipient.getRole() == AccountRole.ADVISOR);

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

        Account student = sender.getRole() == AccountRole.STUDENT
                ? sender
                : recipient;

        Account advisor = sender.getRole() == AccountRole.ADVISOR
                ? sender
                : recipient;

        ChatConversation conversation = getOrCreateConversation(
                student,
                advisor
        );

        ChatMessage message = new ChatMessage(
                conversation.getId(),
                sender.getId(),
                sender.getRole(),
                content
        );

        ChatMessage saved = messageRepository.save(message);

        Query updateQuery = new Query(
                Criteria.where("_id").is(conversation.getId())
                        .orOperator(
                                Criteria.where("last_message_at").exists(false),
                                Criteria.where("last_message_at").lte(saved.getCreatedAt())
                        )
        );
        Update update = new Update()
                .set("last_message", saved.getContent())
                .set("last_message_at", saved.getCreatedAt())
                .set("updated_at", saved.getCreatedAt());
        mongoTemplate.updateFirst(updateQuery, update, ChatConversation.class);

        ChatMessageEvent event = new ChatMessageEvent(
                saved.getId(),
                sender.getId().toString(),
                recipient.getId().toString(),
                saved.getContent(),
                "CHAT",
                saved.getCreatedAt()
        );

        messagingTemplate.convertAndSendToUser(
                recipient.getEmail(), "/queue/messages", event
        );
        messagingTemplate.convertAndSendToUser(
                sender.getEmail(), "/queue/messages", event
        );
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
            return advisorAccountRepository.findAllWithDepartmentByAccessStatus(AccountAccessStatus.ACTIVE)
                    .stream()
                    .map(adv -> new ChatContactResponse(
                            adv.getId(),
                            adv.getFullName(),
                            adv.getRole(),
                            adv.getDepartment() != null ? adv.getDepartment().getId() : null,
                            adv.getDepartment() != null ? adv.getDepartment().getName() : null
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

        List<ChatConversation> rawConversations;
        if (currentUser.getRole() == AccountRole.STUDENT) {
            rawConversations = conversationRepository
                    .findByStudentIdAndConversationType(currentUserId.toString(), "ADVISOR");
        } else if (currentUser.getRole() == AccountRole.ADVISOR) {
            rawConversations = conversationRepository
                    .findByAdvisorIdAndConversationType(currentUserId.toString(), "ADVISOR");
        } else {
            return List.of();
        }

        List<ChatConversation> activeConversations = rawConversations.stream()
                .filter(c -> c.getDeletedAt() == null)
                .toList();

        if (activeConversations.isEmpty()) {
            return List.of();
        }

        Set<UUID> partnerIds = new HashSet<>();
        for (ChatConversation c : activeConversations) {
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

        Map<UUID, AdvisorAccount> advisorMap = Collections.emptyMap();
        if (currentUser.getRole() == AccountRole.STUDENT) {
            advisorMap = advisorAccountRepository.findAllWithDepartmentByIdIn(partnerIds).stream()
                    .collect(Collectors.toMap(AdvisorAccount::getId, Function.identity()));
        }

        List<ChatConversationResponse> result = new ArrayList<>();
        for (ChatConversation conv : activeConversations) {
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
                AdvisorAccount advisor = advisorMap.get(partnerId);
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

            result.add(new ChatConversationResponse(
                    conv.getId(),
                    partner.getId(),
                    partner.getFullName(),
                    partner.getRole(),
                    departmentName,
                    lastMsg != null ? lastMsg : "",
                    lastMsgAt != null ? lastMsgAt : conv.getCreatedAt()
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

        boolean allowedPair =
                (currentUser.getRole() == AccountRole.STUDENT
                        && contact.getRole() == AccountRole.ADVISOR)
                        || (currentUser.getRole() == AccountRole.ADVISOR
                        && contact.getRole() == AccountRole.STUDENT);

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

        if (conversation.isEmpty()) {
            return new ChatHistoryResponse(List.of(), false);
        }

        var slice = messageRepository
                .findByConversationIdOrderByCreatedAtDescIdDesc(
                        conversation.get().getId(),
                        PageRequest.of(page, size)
                );

        List<ChatMessageResponse> messages = new ArrayList<>(
                slice.getContent().stream()
                        .map(message -> {
                            String senderId = message.getSenderUserId();

                            String recipientId = senderId.equals(currentUserId.toString())
                                    ? contactId.toString()
                                    : currentUserId.toString();

                            return new ChatMessageResponse(
                                    message.getId(),
                                    senderId,
                                    recipientId,
                                    message.getContent(),
                                    message.getCreatedAt()
                            );
                        })
                        .toList()
        );

        Collections.reverse(messages);

        return new ChatHistoryResponse(messages, slice.hasNext());
    }

    private ChatConversation getOrCreateConversation(
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
                    new ChatConversation(
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
