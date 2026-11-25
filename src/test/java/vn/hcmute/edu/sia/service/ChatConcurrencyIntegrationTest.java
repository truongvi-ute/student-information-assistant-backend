package vn.hcmute.edu.sia.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.util.ReflectionTestUtils;
import vn.hcmute.edu.sia.dto.request.MarkChatReadRequest;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.dto.response.ChatConversationResponse;
import vn.hcmute.edu.sia.dto.response.ChatReadReceiptEvent;
import vn.hcmute.edu.sia.dto.response.MarkChatReadResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.ChatConversation;
import vn.hcmute.edu.sia.entity.ChatMessage;
import vn.hcmute.edu.sia.entity.DirectConversation;
import vn.hcmute.edu.sia.entity.UserMessage;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.AdvisorProfileRepository;
import vn.hcmute.edu.sia.repository.ChatConversationRepository;
import vn.hcmute.edu.sia.repository.ChatMessageRepository;
import vn.hcmute.edu.sia.repository.DepartmentRepository;
import vn.hcmute.edu.sia.service.implement.ChatServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class ChatConcurrencyIntegrationTest {

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private MongoMappingContext mongoMappingContext;
    @Autowired private ChatConversationRepository conversationRepository;
    @Autowired private ChatMessageRepository messageRepository;

    private AccountRepository accountRepository;
    private AdvisorProfileRepository advisorProfileRepository;
    private DepartmentRepository departmentRepository;
    private RecordingSimpMessagingTemplate messagingTemplate;
    private ChatServiceImpl chatService;

    private UUID studentId;
    private UUID advisorId1;
    private UUID advisorId2;
    private Account student;
    private Account advisor1;
    private Account advisor2;

    @BeforeEach
    void setUp() {
        ensureEntityIndexes();

        accountRepository = mock(AccountRepository.class);
        advisorProfileRepository = mock(AdvisorProfileRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        messagingTemplate = new RecordingSimpMessagingTemplate();

        chatService = new ChatServiceImpl(
                accountRepository,
                advisorProfileRepository,
                departmentRepository,
                messagingTemplate,
                conversationRepository,
                messageRepository,
                mongoTemplate
        );

        studentId = UUID.randomUUID();
        advisorId1 = UUID.randomUUID();
        advisorId2 = UUID.randomUUID();

        student = createAccount(studentId, "Sinh viên Test", "student.int." + studentId + "@hcmute.edu.vn", AccountRole.STUDENT);
        advisor1 = createAccount(advisorId1, "Cố vấn Test 1", "advisor1.int." + advisorId1 + "@hcmute.edu.vn", AccountRole.ADVISOR);
        advisor2 = createAccount(advisorId2, "Cố vấn Test 2", "advisor2.int." + advisorId2 + "@hcmute.edu.vn", AccountRole.ADVISOR);

        lenient().when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        lenient().when(accountRepository.findById(advisorId1)).thenReturn(Optional.of(advisor1));
        lenient().when(accountRepository.findById(advisorId2)).thenReturn(Optional.of(advisor2));
        lenient().when(accountRepository.findAllById(any())).thenReturn(List.of(advisor1, advisor2));
        lenient().when(advisorProfileRepository.findAllWithAccountAndDepartmentByAccountIdIn(any(), eq(AccountRole.ADVISOR)))
                .thenReturn(List.of());
    }

    @AfterEach
    void cleanUpMongo() {
        List<DirectConversation> createdConvs = conversationRepository
                .findByStudentIdAndConversationType(studentId.toString(), "ADVISOR");
        if (!createdConvs.isEmpty()) {
            List<String> convIds = createdConvs.stream().map(DirectConversation::getId).toList();
            mongoTemplate.remove(new Query(Criteria.where("conversation_id").in(convIds)), UserMessage.class);
            mongoTemplate.remove(new Query(Criteria.where("_id").in(convIds)), ChatConversation.class);
        }
    }

    @Test
    @DisplayName("MongoDB thật: nhiều luồng gửi song song cùng clientMessageId chỉ lưu 1 bản ghi và gửi 1 thông báo tới người nhận")
    void sendPrivateMessage_ConcurrentSameClientMessageId_RealMongoDeduplicates() throws Exception {
        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        String clientMsgId = "race-" + UUID.randomUUID();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    chatService.sendPrivateMessage(
                            studentId,
                            new SendChatMessageRequest(advisorId1, "Tin nhắn đồng thời", clientMsgId)
                    );
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        DirectConversation conv = conversationRepository
                .findByStudentIdAndAdvisorIdAndConversationType(studentId.toString(), advisorId1.toString(), "ADVISOR")
                .orElseThrow();

        long storedCount = mongoTemplate.count(
                new Query(Criteria.where("conversation_id").is(conv.getId())),
                UserMessage.class
        );
        assertThat(storedCount).isEqualTo(1L);

        long advisorReceives = messagingTemplate.sent.stream()
                .filter(s -> s.user().equals(advisor1.getEmail()) && s.destination().equals("/queue/messages"))
                .count();
        long studentAcks = messagingTemplate.sent.stream()
                .filter(s -> s.user().equals(student.getEmail()) && s.destination().equals("/queue/messages"))
                .count();

        assertThat(advisorReceives).isEqualTo(1L);
        assertThat(studentAcks).isEqualTo(threads);
    }

    @Test
    @DisplayName("MongoDB thật: retry sau khi lưu tin thành công tự vá lastMessage, phát bù 1 lần và không lặp ở lần retry sau")
    void sendPrivateMessage_RetryRepairsStaleConversationLastMessageAndDoesNotRepeatAfterHealed() {
        DirectConversation conv = conversationRepository.insert(new DirectConversation(studentId, advisorId1));
        Instant failedEarlierAt = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MILLIS);
        UserMessage stored = new UserMessage(conv.getId(), studentId, AccountRole.STUDENT, "Tin bị rớt cập nhật", "retry-heal-1");
        ReflectionTestUtils.setField(stored, "createdAt", failedEarlierAt);
        ReflectionTestUtils.setField(stored, "updatedAt", failedEarlierAt);
        messageRepository.save(stored);

        chatService.sendPrivateMessage(
                studentId,
                new SendChatMessageRequest(advisorId1, "Tin bị rớt cập nhật", "retry-heal-1")
        );

        DirectConversation reloaded = mongoTemplate.findById(conv.getId(), DirectConversation.class);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getLastMessage()).isEqualTo("Tin bị rớt cập nhật");
        assertThat(reloaded.getLastMessageAt()).isEqualTo(failedEarlierAt);

        long msgCount = mongoTemplate.count(new Query(Criteria.where("conversation_id").is(conv.getId())), UserMessage.class);
        assertThat(msgCount).isEqualTo(1L);

        assertThat(messagingTemplate.sent)
                .filteredOn(s -> s.user().equals(advisor1.getEmail()) && s.destination().equals("/queue/messages"))
                .hasSize(1);
        assertThat(messagingTemplate.sent)
                .filteredOn(s -> s.user().equals(student.getEmail()) && s.destination().equals("/queue/messages"))
                .hasSize(1);

        messagingTemplate.sent.clear();

        chatService.sendPrivateMessage(
                studentId,
                new SendChatMessageRequest(advisorId1, "Tin bị rớt cập nhật", "retry-heal-1")
        );

        assertThat(messagingTemplate.sent)
                .filteredOn(s -> s.user().equals(advisor1.getEmail()))
                .isEmpty();
        assertThat(messagingTemplate.sent)
                .filteredOn(s -> s.user().equals(student.getEmail()) && s.destination().equals("/queue/messages"))
                .hasSize(1);
    }

    @Test
    @DisplayName("MongoDB thật: hai luồng đánh dấu đã đọc đua song song giữ nguyên mốc mới nhất và không trả về mốc cũ sai lệch")
    void markConversationAsRead_ConcurrentTabsRace_PreservesMaxAndReturnsActualWatermark() throws Exception {
        DirectConversation conv = conversationRepository.insert(new DirectConversation(studentId, advisorId1));

        Instant olderTime = Instant.parse("2026-11-24T10:05:00Z");
        Instant newerTime = Instant.parse("2026-11-24T10:10:00Z");

        UserMessage msg1 = new UserMessage(conv.getId(), advisorId1, AccountRole.ADVISOR, "Tin 1");
        ReflectionTestUtils.setField(msg1, "createdAt", olderTime);
        UserMessage msg2 = new UserMessage(conv.getId(), advisorId1, AccountRole.ADVISOR, "Tin 2");
        ReflectionTestUtils.setField(msg2, "createdAt", newerTime);
        messageRepository.saveAll(List.of(msg1, msg2));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        MarkChatReadResponse olderRes;
        MarkChatReadResponse newerRes;

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<MarkChatReadResponse> f1 = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return chatService.markConversationAsRead(
                        studentId,
                        new MarkChatReadRequest(advisorId1, msg1.getId(), null)
                );
            });
            Future<MarkChatReadResponse> f2 = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return chatService.markConversationAsRead(
                        studentId,
                        new MarkChatReadRequest(advisorId1, msg2.getId(), null)
                );
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            olderRes = f1.get(10, TimeUnit.SECONDS);
            newerRes = f2.get(10, TimeUnit.SECONDS);
        }

        assertThat(newerRes.updated()).isTrue();
        assertThat(newerRes.lastReadAt()).isEqualTo(newerTime);
        if (!olderRes.updated()) {
            assertThat(olderRes.lastReadAt()).isEqualTo(newerTime);
        } else {
            assertThat(olderRes.lastReadAt()).isEqualTo(olderTime);
        }

        messagingTemplate.sent.clear();

        MarkChatReadResponse staleRes = chatService.markConversationAsRead(
                studentId,
                new MarkChatReadRequest(advisorId1, msg1.getId(), null)
        );
        assertThat(staleRes.updated()).isFalse();
        assertThat(staleRes.lastReadAt()).isEqualTo(newerTime);
        assertThat(messagingTemplate.sent).isEmpty();

        DirectConversation reloaded = mongoTemplate.findById(conv.getId(), DirectConversation.class);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getStudentLastReadAt()).isEqualTo(newerTime);

        MarkChatReadResponse initialCheck = chatService.markConversationAsRead(
                advisorId1,
                new MarkChatReadRequest(studentId, msg1.getId(), null)
        );
        assertThat(initialCheck.updated()).isTrue();
        assertThat(messagingTemplate.sent)
                .extracting(SentFrame::destination)
                .containsOnly("/queue/read-receipts");
        assertThat(messagingTemplate.sent)
                .allMatch(f -> f.payload() instanceof ChatReadReceiptEvent);
    }

    @Test
    @DisplayName("MongoDB thật: getConversations chạy Aggregation tính chính xác unreadCount theo studentLastReadAt")
    void getConversations_RealMongoAggregation_ComputesAccurateUnreadCounts() {
        Instant t1 = Instant.parse("2026-11-24T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-24T10:01:00Z");
        Instant t3 = Instant.parse("2026-11-24T10:02:00Z");
        Instant t4 = Instant.parse("2026-11-24T10:03:00Z");

        DirectConversation conv1 = new DirectConversation(studentId, advisorId1);
        ReflectionTestUtils.setField(conv1, "lastMessage", "Em phản hồi lại nhé");
        ReflectionTestUtils.setField(conv1, "lastMessageAt", t4);
        ReflectionTestUtils.setField(conv1, "studentLastReadAt", t1);
        conversationRepository.insert(conv1);

        DirectConversation conv2 = new DirectConversation(studentId, advisorId2);
        ReflectionTestUtils.setField(conv2, "lastMessage", "Đã giải quyết xong");
        ReflectionTestUtils.setField(conv2, "lastMessageAt", t2);
        ReflectionTestUtils.setField(conv2, "studentLastReadAt", t2);
        conversationRepository.insert(conv2);

        UserMessage c1Advisor1 = msgAt(conv1.getId(), advisorId1, AccountRole.ADVISOR, "Tin cũ đã đọc", t1);
        UserMessage c1Advisor2 = msgAt(conv1.getId(), advisorId1, AccountRole.ADVISOR, "Chưa đọc 1", t2);
        UserMessage c1Advisor3 = msgAt(conv1.getId(), advisorId1, AccountRole.ADVISOR, "Chưa đọc 2", t3);
        UserMessage c1Student4 = msgAt(conv1.getId(), studentId, AccountRole.STUDENT, "Tin của chính mình", t4);
        UserMessage c2Advisor1 = msgAt(conv2.getId(), advisorId2, AccountRole.ADVISOR, "Đã đọc 1", t1);
        UserMessage c2Advisor2 = msgAt(conv2.getId(), advisorId2, AccountRole.ADVISOR, "Đã đọc 2", t2);
        messageRepository.saveAll(List.of(c1Advisor1, c1Advisor2, c1Advisor3, c1Student4, c2Advisor1, c2Advisor2));

        List<ChatConversationResponse> responses = chatService.getConversations(studentId);
        assertThat(responses).hasSize(2);

        ChatConversationResponse r1 = responses.stream()
                .filter(r -> r.conversationId().equals(conv1.getId()))
                .findFirst()
                .orElseThrow();
        ChatConversationResponse r2 = responses.stream()
                .filter(r -> r.conversationId().equals(conv2.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(r1.unreadCount()).isEqualTo(2L);
        assertThat(r2.unreadCount()).isEqualTo(0L);
    }

    private void ensureEntityIndexes() {
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
        IndexOperations convIndexOps = mongoTemplate.indexOps(ChatConversation.class);
        resolver.resolveIndexFor(ChatConversation.class).forEach(convIndexOps::createIndex);

        IndexOperations msgIndexOps = mongoTemplate.indexOps(ChatMessage.class);
        resolver.resolveIndexFor(ChatMessage.class).forEach(msgIndexOps::createIndex);
    }

    private static Account createAccount(UUID id, String fullName, String email, AccountRole role) {
        Account account = new Account(fullName, email, "hash", role, AccountAccessStatus.ACTIVE, false);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static UserMessage msgAt(
            String conversationId,
            UUID senderId,
            AccountRole role,
            String content,
            Instant createdAt
    ) {
        UserMessage msg = new UserMessage(conversationId, senderId, role, content);
        ReflectionTestUtils.setField(msg, "createdAt", createdAt);
        ReflectionTestUtils.setField(msg, "updatedAt", createdAt);
        return msg;
    }

    private record SentFrame(String user, String destination, Object payload) {}

    private static class RecordingSimpMessagingTemplate extends SimpMessagingTemplate {
        private final CopyOnWriteArrayList<SentFrame> sent = new CopyOnWriteArrayList<>();

        RecordingSimpMessagingTemplate() {
            super(new ExecutorSubscribableChannel());
        }

        @Override
        public void convertAndSendToUser(String user, String destination, Object payload) {
            sent.add(new SentFrame(user, destination, payload));
        }
    }
}
