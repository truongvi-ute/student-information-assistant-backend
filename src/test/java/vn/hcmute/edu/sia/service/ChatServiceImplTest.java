package vn.hcmute.edu.sia.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.request.MarkChatReadRequest;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.dto.response.ChatConversationResponse;
import vn.hcmute.edu.sia.dto.response.ChatHistoryResponse;
import vn.hcmute.edu.sia.dto.response.ChatMessageEvent;
import vn.hcmute.edu.sia.dto.response.ChatReadReceiptEvent;
import vn.hcmute.edu.sia.dto.response.MarkChatReadResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.AdvisorProfile;
import vn.hcmute.edu.sia.entity.ChatConversation;
import vn.hcmute.edu.sia.entity.ChatMessage;
import vn.hcmute.edu.sia.entity.Department;
import vn.hcmute.edu.sia.entity.DirectConversation;
import vn.hcmute.edu.sia.entity.UserMessage;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.MessageSenderType;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.AdvisorProfileRepository;
import vn.hcmute.edu.sia.repository.ChatConversationRepository;
import vn.hcmute.edu.sia.repository.ChatMessageRepository;
import vn.hcmute.edu.sia.repository.DepartmentRepository;
import vn.hcmute.edu.sia.service.implement.ChatServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AdvisorProfileRepository advisorProfileRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private ChatServiceImpl chatService;

    @Mock private Account student;
    @Mock private Account advisor;
    @Mock private Account admin;

    private UUID studentId;
    private UUID advisorId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        advisorId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Gửi tin nhắn riêng giữa Student và Advisor tạo DirectConversation, lưu UserMessage và gửi STOMP")
    void sendPrivateMessage_StudentToAdvisor_Success() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getId()).thenReturn(studentId);
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(student.getEmail()).thenReturn("student@hcmute.edu.vn");
        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getEmail()).thenReturn("advisor@hcmute.edu.vn");

        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        )).thenReturn(Optional.empty());
        when(conversationRepository.insert(any(DirectConversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.save(any(UserMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SendChatMessageRequest request = new SendChatMessageRequest(advisorId, "  Thầy ơi cho em hỏi lịch học  ");
        chatService.sendPrivateMessage(studentId, request);

        ArgumentCaptor<UserMessage> msgCaptor = ArgumentCaptor.forClass(UserMessage.class);
        verify(messageRepository).save(msgCaptor.capture());
        UserMessage savedMsg = msgCaptor.getValue();

        assertThat(savedMsg.getSenderUserId()).isEqualTo(studentId.toString());
        assertThat(savedMsg.getSenderType()).isEqualTo(MessageSenderType.STUDENT);
        assertThat(savedMsg.getContent()).isEqualTo("Thầy ơi cho em hỏi lịch học");

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(ChatConversation.class));
        verify(messagingTemplate).convertAndSendToUser(eq("advisor@hcmute.edu.vn"), eq("/queue/messages"), any(ChatMessageEvent.class));
        verify(messagingTemplate).convertAndSendToUser(eq("student@hcmute.edu.vn"), eq("/queue/messages"), any(ChatMessageEvent.class));
    }

    @Test
    @DisplayName("Gửi tin nhắn xử lý DuplicateKeyException khi tạo DirectConversation đồng thời")
    void sendPrivateMessage_DuplicateKeyOnCreate_LoadsExistingConversation() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getId()).thenReturn(studentId);
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(student.getEmail()).thenReturn("student@hcmute.edu.vn");
        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getEmail()).thenReturn("advisor@hcmute.edu.vn");

        DirectConversation existing = new DirectConversation(studentId, advisorId);
        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        when(conversationRepository.insert(any(DirectConversation.class)))
                .thenThrow(new DuplicateKeyException("Duplicate pair"));
        when(messageRepository.save(any(UserMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        chatService.sendPrivateMessage(studentId, new SendChatMessageRequest(advisorId, "Xin chào"));

        verify(messageRepository).save(any(UserMessage.class));
    }

    @Test
    @DisplayName("Từ chối gửi tin nhắn nếu không phải cặp Student - Advisor")
    void sendPrivateMessage_InvalidRolePair_ThrowsException() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(admin.getRole()).thenReturn(AccountRole.ADMIN);

        assertThatThrownBy(() -> chatService.sendPrivateMessage(
                studentId, new SendChatMessageRequest(adminId, "Hello")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chat is only allowed between advisor and student");
    }

    @Test
    @DisplayName("Lấy danh sách DirectConversation cho Student kèm thông tin khoa của Advisor")
    void getConversations_Student_Success() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getRole()).thenReturn(AccountRole.STUDENT);

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        when(conversationRepository.findByStudentIdAndConversationType(studentId.toString(), "ADVISOR"))
                .thenReturn(List.of(conv));

        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getFullName()).thenReturn("TS. Nguyễn Văn A");
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(accountRepository.findAllById(any())).thenReturn(List.of(advisor));

        Department dept = new Department("Khoa CNTT");
        AdvisorProfile profile = mock(AdvisorProfile.class);
        when(profile.getAccount()).thenReturn(advisor);
        when(profile.getDepartment()).thenReturn(dept);
        when(advisorProfileRepository.findAllWithAccountAndDepartmentByAccountIdIn(any(), eq(AccountRole.ADVISOR)))
                .thenReturn(List.of(profile));

        UserMessage latestMsg = new UserMessage(conv.getId(), advisorId, AccountRole.ADVISOR, "Em xem quy chế nhé");
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conv.getId()))
                .thenReturn(Optional.of(latestMsg));

        List<ChatConversationResponse> responses = chatService.getConversations(studentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().partnerId()).isEqualTo(advisorId);
        assertThat(responses.getFirst().partnerName()).isEqualTo("TS. Nguyễn Văn A");
        assertThat(responses.getFirst().partnerDepartment()).isEqualTo("Khoa CNTT");
        assertThat(responses.getFirst().lastMessage()).isEqualTo("Em xem quy chế nhé");
    }

    @Test
    @DisplayName("Lấy lịch sử Direct Chat đảo ngược về thứ tự thời gian tăng dần")
    void getHistory_StudentAndAdvisor_ReturnsChronologicalOrder() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        )).thenReturn(Optional.of(conv));

        UserMessage olderMsg = new UserMessage(conv.getId(), studentId, AccountRole.STUDENT, "Tin nhắn 1");
        UserMessage newerMsg = new UserMessage(conv.getId(), advisorId, AccountRole.ADVISOR, "Tin nhắn 2");
        List<ChatMessage> sliceContentDesc = List.of(newerMsg, olderMsg);

        when(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(eq(conv.getId()), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(sliceContentDesc, Pageable.ofSize(30), false));

        ChatHistoryResponse history = chatService.getHistory(studentId, advisorId, 0, 30);

        assertThat(history.conversationId()).isEqualTo(conv.getId());
        assertThat(history.hasNext()).isFalse();
        assertThat(history.messages()).hasSize(2);
        assertThat(history.messages().get(0).conversationId()).isEqualTo(conv.getId());
        assertThat(history.messages().get(0).message()).isEqualTo("Tin nhắn 1");
        assertThat(history.messages().get(0).senderId()).isEqualTo(studentId.toString());
        assertThat(history.messages().get(0).recipientId()).isEqualTo(advisorId.toString());
        assertThat(history.messages().get(1).message()).isEqualTo("Tin nhắn 2");
        assertThat(history.messages().get(1).senderId()).isEqualTo(advisorId.toString());
        assertThat(history.messages().get(1).recipientId()).isEqualTo(studentId.toString());
    }

    @Test
    @DisplayName("Gửi lại tin nhắn trùng clientMessageId không lưu bản ghi mới và chỉ xác nhận lại cho người gửi")
    void sendPrivateMessage_DuplicateClientMessageIdPreCheck_AcksOnlySender() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getId()).thenReturn(studentId);
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(student.getEmail()).thenReturn("student@hcmute.edu.vn");
        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        )).thenReturn(Optional.of(conv));

        UserMessage existing = new UserMessage(conv.getId(), studentId, AccountRole.STUDENT, "Tin cũ", "client-uuid-1");
        when(mongoTemplate.findOne(any(Query.class), eq(UserMessage.class))).thenReturn(existing);

        chatService.sendPrivateMessage(studentId, new SendChatMessageRequest(advisorId, "Tin cũ", "  client-uuid-1  "));

        verify(messageRepository, never()).save(any());
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), any(Update.class), eq(ChatConversation.class));
        List<Document> orBranches = queryCaptor.getValue().getQueryObject().getList("$or", Document.class);
        assertThat(orBranches).anySatisfy(branch ->
                assertThat(branch.get("last_message_at", Document.class)).containsKey("$lt")
        );

        ArgumentCaptor<ChatMessageEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq("student@hcmute.edu.vn"), eq("/queue/messages"), eventCaptor.capture());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("advisor@hcmute.edu.vn"), anyString(), any());

        assertThat(eventCaptor.getValue().type()).isEqualTo("CHAT");
        assertThat(eventCaptor.getValue().messageId()).isEqualTo(existing.getId());
        assertThat(eventCaptor.getValue().clientMessageId()).isEqualTo("client-uuid-1");
        assertThat(eventCaptor.getValue().conversationId()).isEqualTo(conv.getId());
    }

    @Test
    @DisplayName("Đua ghi song song trùng clientMessageId gặp DuplicateKeyException chỉ xác nhận lại cho người gửi")
    void sendPrivateMessage_ConcurrentDuplicateKey_AcksOnlySender() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getId()).thenReturn(studentId);
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(student.getEmail()).thenReturn("student@hcmute.edu.vn");
        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        )).thenReturn(Optional.of(conv));

        UserMessage existing = new UserMessage(conv.getId(), studentId, AccountRole.STUDENT, "Tin song song", "client-race-1");
        when(mongoTemplate.findOne(any(Query.class), eq(UserMessage.class)))
                .thenReturn(null)
                .thenReturn(existing);
        when(messageRepository.save(any(UserMessage.class)))
                .thenThrow(new DuplicateKeyException("uq_direct_msg_client_id"));

        chatService.sendPrivateMessage(studentId, new SendChatMessageRequest(advisorId, "Tin song song", "client-race-1"));

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(ChatConversation.class));
        verify(messagingTemplate).convertAndSendToUser(eq("student@hcmute.edu.vn"), eq("/queue/messages"), any(ChatMessageEvent.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("advisor@hcmute.edu.vn"), anyString(), any());
    }

    @Test
    @DisplayName("Đánh dấu đã đọc dùng findAndModify $max theo createdAt và phát ChatReadReceiptEvent tới cả 2 phía")
    void markConversationAsRead_ByLastMessageId_UpdatesMaxAndBroadcastsToBoth() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getId()).thenReturn(studentId);
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(student.getEmail()).thenReturn("student@hcmute.edu.vn");
        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(advisor.getEmail()).thenReturn("advisor@hcmute.edu.vn");

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        )).thenReturn(Optional.of(conv));

        Instant displayedCreatedAt = Instant.parse("2026-11-24T10:15:30Z");
        UserMessage displayedMsg = new UserMessage(conv.getId(), advisorId, AccountRole.ADVISOR, "Thầy đã xem hồ sơ");
        ReflectionTestUtils.setField(displayedMsg, "createdAt", displayedCreatedAt);

        when(messageRepository.findById(displayedMsg.getId())).thenReturn(Optional.of(displayedMsg));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(DirectConversation.class)
        )).thenReturn(conv);

        MarkChatReadResponse response = chatService.markConversationAsRead(
                studentId,
                new MarkChatReadRequest(advisorId, displayedMsg.getId(), null)
        );

        assertThat(response.updated()).isTrue();
        assertThat(response.conversationId()).isEqualTo(conv.getId());
        assertThat(response.lastReadAt()).isEqualTo(displayedCreatedAt);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                any(Query.class),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(DirectConversation.class)
        );
        Document updateDoc = updateCaptor.getValue().getUpdateObject();
        assertThat(updateDoc.get("$max", Document.class).get("student_last_read_at")).isEqualTo(displayedCreatedAt);

        ArgumentCaptor<ChatReadReceiptEvent> receiptCaptor = ArgumentCaptor.forClass(ChatReadReceiptEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq("advisor@hcmute.edu.vn"), eq("/queue/read-receipts"), receiptCaptor.capture());
        verify(messagingTemplate).convertAndSendToUser(eq("student@hcmute.edu.vn"), eq("/queue/read-receipts"), any(ChatReadReceiptEvent.class));

        assertThat(receiptCaptor.getValue().type()).isEqualTo("READ_RECEIPT");
        assertThat(receiptCaptor.getValue().readerId()).isEqualTo(studentId.toString());
        assertThat(receiptCaptor.getValue().lastReadAt()).isEqualTo(displayedCreatedAt);
    }

    @Test
    @DisplayName("Đánh dấu đã đọc khi tab khác vừa nâng mốc mới hơn trả đúng mốc DB và không phát lặp READ_RECEIPT")
    void markConversationAsRead_ConcurrentNewerWatermark_ReturnsActualDbWatermark() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);

        Instant initialReadAt = Instant.parse("2026-11-24T10:00:00Z");
        Instant tabAReadAt = Instant.parse("2026-11-24T10:10:00Z");
        Instant tabBTargetReadAt = Instant.parse("2026-11-24T10:05:00Z");

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        ReflectionTestUtils.setField(conv, "lastMessageAt", tabAReadAt);
        ReflectionTestUtils.setField(conv, "studentLastReadAt", initialReadAt);

        DirectConversation stateJustBeforeUpdate = new DirectConversation(studentId, advisorId);
        ReflectionTestUtils.setField(stateJustBeforeUpdate, "lastMessageAt", tabAReadAt);
        ReflectionTestUtils.setField(stateJustBeforeUpdate, "studentLastReadAt", tabAReadAt);

        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        )).thenReturn(Optional.of(conv));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(DirectConversation.class)
        )).thenReturn(stateJustBeforeUpdate);

        MarkChatReadResponse response = chatService.markConversationAsRead(
                studentId,
                new MarkChatReadRequest(advisorId, null, tabBTargetReadAt)
        );

        assertThat(response.updated()).isFalse();
        assertThat(response.lastReadAt()).isEqualTo(tabAReadAt);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Từ chối đánh dấu đã đọc nếu lastMessageId thuộc hội thoại khác")
    void markConversationAsRead_MessageFromOtherConversation_ThrowsBadRequest() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));
        when(student.getRole()).thenReturn(AccountRole.STUDENT);
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        when(conversationRepository.findByStudentIdAndAdvisorIdAndConversationType(
                studentId.toString(), advisorId.toString(), "ADVISOR"
        )).thenReturn(Optional.of(conv));

        UserMessage foreignMsg = new UserMessage("other-conv", advisorId, AccountRole.ADVISOR, "Tin khác");
        when(messageRepository.findById(foreignMsg.getId())).thenReturn(Optional.of(foreignMsg));

        assertThatThrownBy(() -> chatService.markConversationAsRead(
                studentId,
                new MarkChatReadRequest(advisorId, foreignMsg.getId(), null)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to this conversation");
    }

    @Test
    @DisplayName("getConversations tính unreadCount từ aggregation theo mốc đọc và trả kèm partnerLastReadAt")
    void getConversations_ComputesUnreadCountAndPartnerLastReadAt() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(student.getRole()).thenReturn(AccountRole.STUDENT);

        DirectConversation conv = new DirectConversation(studentId, advisorId);
        Instant partnerReadAt = Instant.parse("2026-11-24T09:00:00Z");
        Instant msgAt = Instant.parse("2026-11-24T10:00:00Z");
        ReflectionTestUtils.setField(conv, "lastMessage", "Em kiểm tra lại lịch thi nhé");
        ReflectionTestUtils.setField(conv, "lastMessageAt", msgAt);
        ReflectionTestUtils.setField(conv, "advisorLastReadAt", partnerReadAt);

        when(conversationRepository.findByStudentIdAndConversationType(studentId.toString(), "ADVISOR"))
                .thenReturn(List.of(conv));

        when(advisor.getId()).thenReturn(advisorId);
        when(advisor.getFullName()).thenReturn("TS. Nguyễn Văn A");
        when(advisor.getRole()).thenReturn(AccountRole.ADVISOR);
        when(accountRepository.findAllById(any())).thenReturn(List.of(advisor));
        when(advisorProfileRepository.findAllWithAccountAndDepartmentByAccountIdIn(any(), eq(AccountRole.ADVISOR)))
                .thenReturn(List.of());

        AggregationResults<Document> aggResults = new AggregationResults<>(
                List.of(new Document("_id", conv.getId()).append("unreadCount", 3L)),
                new Document()
        );
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("messages"), eq(Document.class)))
                .thenReturn(aggResults);

        List<ChatConversationResponse> responses = chatService.getConversations(studentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().unreadCount()).isEqualTo(3L);
        assertThat(responses.getFirst().partnerLastReadAt()).isEqualTo(partnerReadAt);
    }
}
