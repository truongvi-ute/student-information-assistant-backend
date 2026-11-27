package vn.hcmute.edu.sia.service;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatHistoryMessage;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatRequest;
import vn.hcmute.edu.sia.dto.internal.AiServiceChatResponse;
import vn.hcmute.edu.sia.dto.internal.AiServiceSourceCitation;
import vn.hcmute.edu.sia.dto.request.CreateAiConversationRequest;
import vn.hcmute.edu.sia.dto.request.SendAiMessageRequest;
import vn.hcmute.edu.sia.dto.response.AiChatbotHistoryResponse;
import vn.hcmute.edu.sia.dto.response.AiChatbotTurnResponse;
import vn.hcmute.edu.sia.dto.response.AiConversationResponse;
import vn.hcmute.edu.sia.entity.AIConversation;
import vn.hcmute.edu.sia.entity.AIMessage;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.ChatMessage;
import vn.hcmute.edu.sia.entity.Citation;
import vn.hcmute.edu.sia.entity.Document;
import vn.hcmute.edu.sia.entity.UserMessage;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.MessageSenderType;
import vn.hcmute.edu.sia.exception.AiServiceException;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.ChatConversationRepository;
import vn.hcmute.edu.sia.repository.ChatMessageRepository;
import vn.hcmute.edu.sia.repository.DocumentRepository;
import vn.hcmute.edu.sia.service.implement.AiChatbotServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatbotServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private AiServiceClient aiServiceClient;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private AiChatbotServiceImpl aiChatbotService;

    private UUID studentId;
    private UUID advisorId;
    private UUID documentId;
    private Account activeStudent;
    private Account activeAdvisor;

    @BeforeEach
    void setUp() throws Exception {
        studentId = UUID.randomUUID();
        advisorId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        activeStudent = new Account(
                "Nguyễn Văn Sinh Viên",
                "student@hcmute.edu.vn",
                "hash",
                AccountRole.STUDENT,
                AccountAccessStatus.ACTIVE,
                false
        );
        setField(activeStudent, "id", studentId);

        activeAdvisor = new Account(
                "TS. Trần Văn Cố Vấn",
                "advisor@hcmute.edu.vn",
                "hash",
                AccountRole.ADVISOR,
                AccountAccessStatus.ACTIVE,
                false
        );
        setField(activeAdvisor, "id", advisorId);
    }

    @Test
    @DisplayName("Tạo AIConversation thành công cho tài khoản STUDENT đang hoạt động")
    void createConversation_ActiveStudent_Success() {
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.save(any(AIConversation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AiConversationResponse response = aiChatbotService.createConversation(
                studentId,
                new CreateAiConversationRequest(null, null)
        );

        assertThat(response.conversationId()).isNotBlank();
        assertThat(response.title()).isEqualTo(AIConversation.DEFAULT_TITLE);
    }

    @Test
    @DisplayName("Chặn tài khoản ADVISOR sử dụng AI Chatbot với mã 403 FORBIDDEN")
    void createConversation_AdvisorRole_ThrowsForbidden() {
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(activeAdvisor));

        assertThatThrownBy(() -> aiChatbotService.createConversation(advisorId, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Hỏi đáp AI lưu cặp UserMessage + AIMessage kèm Citation và tự đặt tiêu đề")
    @SuppressWarnings("unchecked")
    void askQuestion_Success_SavesUserMessageAndAIMessageWithCitations() throws Exception {
        AIConversation conversation = new AIConversation(studentId, null, null);
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                conversation.getId(), studentId.toString(), "AI"
        )).thenReturn(Optional.of(conversation));

        Document matchedDoc = new Document(
                activeAdvisor,
                "Quy chế học vụ 2026",
                "Mô tả",
                true,
                "quy_che_2026.pdf",
                "documents/quy_che_2026.pdf",
                "application/pdf",
                2048L,
                "a".repeat(64)
        );
        setField(matchedDoc, "id", documentId);
        when(documentRepository.findByOriginalFilenameIn(anyCollection()))
                .thenReturn(List.of(matchedDoc));

        AiServiceSourceCitation sourceCitation = new AiServiceSourceCitation(
                "quy_che_2026.pdf",
                12,
                4,
                0.89,
                "Sinh viên đạt GPA từ 3.2 trở lên..."
        );
        AiServiceChatResponse aiResponse = new AiServiceChatResponse(
                "Điều kiện nhận học bổng KKHT là gì?",
                "Theo quy chế học vụ, sinh viên cần GPA từ 3.2 trở lên.",
                List.of(sourceCitation)
        );
        when(aiServiceClient.chat(any(AiServiceChatRequest.class))).thenReturn(aiResponse);

        AiChatbotTurnResponse turn = aiChatbotService.askQuestion(
                studentId,
                conversation.getId(),
                new SendAiMessageRequest("Điều kiện nhận học bổng KKHT là gì?", 5, null)
        );

        ArgumentCaptor<List<ChatMessage>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(savedCaptor.capture());
        List<ChatMessage> savedMessages = savedCaptor.getValue();

        assertThat(savedMessages).hasSize(2);
        assertThat(savedMessages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(savedMessages.get(1)).isInstanceOf(AIMessage.class);

        UserMessage savedUserMsg = (UserMessage) savedMessages.get(0);
        AIMessage savedAiMsg = (AIMessage) savedMessages.get(1);

        assertThat(savedUserMsg.getSenderUserId()).isEqualTo(studentId.toString());
        assertThat(savedAiMsg.getSenderType()).isEqualTo(MessageSenderType.AI);
        assertThat(savedAiMsg.getCitations()).hasSize(1);
        assertThat(savedAiMsg.getCitations().getFirst().getDocumentId()).isEqualTo(documentId.toString());
        assertThat(savedAiMsg.getCitations().getFirst().getSourceTitle()).isEqualTo("Quy chế học vụ 2026");
        assertThat(savedAiMsg.getCitations().getFirst().getChunkReference()).isEqualTo("chunk_4");

        assertThat(turn.title()).isEqualTo("Điều kiện nhận học bổng KKHT là gì?");
        assertThat(turn.studentMessage().senderType()).isEqualTo(MessageSenderType.STUDENT);
        assertThat(turn.aiMessage().senderType()).isEqualTo(MessageSenderType.AI);
        assertThat(turn.aiMessage().citations()).hasSize(1);
        assertThat(turn.aiMessage().citations().getFirst().documentId()).isEqualTo(documentId);
        assertThat(turn.aiMessage().citations().getFirst().sourceTitle()).isEqualTo("Quy chế học vụ 2026");
        assertThat(turn.aiMessage().citations().getFirst().originalFilename()).isEqualTo("quy_che_2026.pdf");

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(AIConversation.class));
    }

    @Test
    @DisplayName("Không lưu tin nhắn vào MongoDB khi sia-ai-service ném lỗi 504")
    void askQuestion_AiServiceTimeout_DoesNotSaveMessages() {
        AIConversation conversation = new AIConversation(studentId, "Học bổng", null);
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                conversation.getId(), studentId.toString(), "AI"
        )).thenReturn(Optional.of(conversation));
        when(aiServiceClient.chat(any(AiServiceChatRequest.class)))
                .thenThrow(new AiServiceException(HttpStatus.GATEWAY_TIMEOUT, "Timeout"));

        assertThatThrownBy(() -> aiChatbotService.askQuestion(
                studentId,
                conversation.getId(),
                new SendAiMessageRequest("Câu hỏi", 5, null)
        )).isInstanceOf(AiServiceException.class);

        verify(messageRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Lấy lịch sử AIConversation ánh xạ đúng UserMessage và AIMessage kèm Citation")
    void getMessages_ReturnsChronologicalPolymorphicMessages() {
        AIConversation conversation = new AIConversation(studentId, "Tốt nghiệp", null);
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                conversation.getId(), studentId.toString(), "AI"
        )).thenReturn(Optional.of(conversation));

        UserMessage userMsg = new UserMessage(conversation.getId(), studentId, AccountRole.STUDENT, "Câu hỏi 1");
        Citation citation = new Citation(documentId, "qc.pdf", 2, "chunk_1", "Trích dẫn", 0.85);
        AIMessage aiMsg = new AIMessage(conversation.getId(), "Trả lời 1", List.of(citation));

        when(messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                eq(conversation.getId()), any(Pageable.class)
        )).thenReturn(new SliceImpl<>(List.of(aiMsg, userMsg), Pageable.ofSize(30), false));

        AiChatbotHistoryResponse history = aiChatbotService.getMessages(studentId, conversation.getId(), 0, 30);

        assertThat(history.messages()).hasSize(2);
        assertThat(history.messages().get(0).senderType()).isEqualTo(MessageSenderType.STUDENT);
        assertThat(history.messages().get(0).citations()).isEmpty();
        assertThat(history.messages().get(1).senderType()).isEqualTo(MessageSenderType.AI);
        assertThat(history.messages().get(1).citations()).hasSize(1);
    }

    @Test
    @DisplayName("Ưu tiên documentId trực tiếp từ AI-Service và lưu charStart/charEnd cùng full content")
    @SuppressWarnings("unchecked")
    void askQuestion_WithDirectDocumentIdAndOffsets_SkipsFilenameLookupAndSavesFullContent() throws Exception {
        AIConversation conversation = new AIConversation(studentId, "Học bổng", null);
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                conversation.getId(), studentId.toString(), "AI"
        )).thenReturn(Optional.of(conversation));

        Document doc = new Document(
                activeAdvisor,
                "Chương trình đào tạo CNTT 2026",
                null,
                true,
                "ab0dca45_CTDT-CNTT.pdf",
                "documents/ab0dca45_CTDT-CNTT.pdf",
                "application/pdf",
                2048L,
                "b".repeat(64)
        );
        setField(doc, "id", documentId);
        when(documentRepository.findAllById(anyCollection())).thenReturn(List.of(doc));

        String fullChunkText = "Điều 5. Sinh viên đạt GPA từ 3.2 trở lên và điểm rèn luyện từ 80 trở lên sẽ được xét học bổng.";
        AiServiceSourceCitation sourceCitation = new AiServiceSourceCitation(
                documentId.toString(),
                "ab0dca45_CTDT-CNTT.pdf",
                3,
                2,
                140,
                234,
                0.93,
                "Điều 5. Sinh viên đạt GPA...",
                fullChunkText
        );
        AiServiceChatResponse aiResponse = new AiServiceChatResponse(
                "Điều kiện học bổng?",
                "Cần GPA >= 3.2 và ĐRL >= 80.",
                List.of(sourceCitation)
        );
        when(aiServiceClient.chat(any(AiServiceChatRequest.class))).thenReturn(aiResponse);

        AiChatbotTurnResponse turn = aiChatbotService.askQuestion(
                studentId,
                conversation.getId(),
                new SendAiMessageRequest("Điều kiện học bổng?", 5, null)
        );

        verify(documentRepository, never()).findByOriginalFilenameIn(anyCollection());
        ArgumentCaptor<List<ChatMessage>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(savedCaptor.capture());

        AIMessage savedAiMsg = (AIMessage) savedCaptor.getValue().get(1);
        Citation savedCitation = savedAiMsg.getCitations().getFirst();
        assertThat(savedCitation.getDocumentId()).isEqualTo(documentId.toString());
        assertThat(savedCitation.getSourceTitle()).isEqualTo("Chương trình đào tạo CNTT 2026");
        assertThat(savedCitation.getQuotedContent()).isEqualTo(fullChunkText);
        assertThat(savedCitation.getCharStart()).isEqualTo(140);
        assertThat(savedCitation.getCharEnd()).isEqualTo(234);

        var citationDto = turn.aiMessage().citations().getFirst();
        assertThat(citationDto.documentId()).isEqualTo(documentId);
        assertThat(citationDto.sourceTitle()).isEqualTo("Chương trình đào tạo CNTT 2026");
        assertThat(citationDto.originalFilename()).isEqualTo("ab0dca45_CTDT-CNTT.pdf");
        assertThat(citationDto.quotedContent()).isEqualTo(fullChunkText);
        assertThat(citationDto.charStart()).isEqualTo(140);
        assertThat(citationDto.charEnd()).isEqualTo(234);
    }

    @Test
    @DisplayName("Truy vấn tối đa 6 tin nhắn gần nhất từ MongoDB và gửi theo chiều cũ trước mới sau")
    void askQuestion_WithRecentHistory_ForwardsChronologicalHistoryToAiService() {
        AIConversation conversation = new AIConversation(studentId, "Kỹ thuật dữ liệu", null);
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                conversation.getId(), studentId.toString(), "AI"
        )).thenReturn(Optional.of(conversation));

        UserMessage userMsg1 = new UserMessage(
                conversation.getId(),
                studentId,
                AccountRole.STUDENT,
                "Ngành Kỹ thuật dữ liệu học bao nhiêu tín chỉ?"
        );
        AIMessage aiMsg1 = new AIMessage(
                conversation.getId(),
                "Chương trình đào tạo ngành Kỹ thuật dữ liệu có tổng cộng 150 tín chỉ.",
                List.of()
        );
        UserMessage userMsg2 = new UserMessage(
                conversation.getId(),
                studentId,
                AccountRole.STUDENT,
                "Thời gian đào tạo mấy năm?"
        );
        AIMessage aiMsg2 = new AIMessage(
                conversation.getId(),
                "Thời gian đào tạo chuẩn là 4 năm.",
                List.of()
        );

        when(messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                eq(conversation.getId()), eq(PageRequest.of(0, 6))
        )).thenReturn(new SliceImpl<>(
                List.of(aiMsg2, userMsg2, aiMsg1, userMsg1),
                PageRequest.of(0, 6),
                false
        ));

        AiServiceChatResponse aiResponse = new AiServiceChatResponse(
                "Còn chuẩn đầu ra của ngành đó?",
                "Chuẩn đầu ra ngành Kỹ thuật dữ liệu gồm kiến thức chuyên môn và ngoại ngữ.",
                List.of()
        );
        when(aiServiceClient.chat(any(AiServiceChatRequest.class))).thenReturn(aiResponse);

        aiChatbotService.askQuestion(
                studentId,
                conversation.getId(),
                new SendAiMessageRequest("Còn chuẩn đầu ra của ngành đó?", 5, null)
        );

        ArgumentCaptor<AiServiceChatRequest> requestCaptor = ArgumentCaptor.forClass(AiServiceChatRequest.class);
        verify(aiServiceClient).chat(requestCaptor.capture());

        AiServiceChatRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.question()).isEqualTo("Còn chuẩn đầu ra của ngành đó?");
        assertThat(sentRequest.history()).containsExactly(
                new AiServiceChatHistoryMessage("user", userMsg1.getContent()),
                new AiServiceChatHistoryMessage("assistant", aiMsg1.getContent()),
                new AiServiceChatHistoryMessage("user", userMsg2.getContent()),
                new AiServiceChatHistoryMessage("assistant", aiMsg2.getContent())
        );
    }

    @Test
    @DisplayName("Truyền danh sách documentId ACTIVE/READY sang AI-Service trước khi gọi LLM và loại citation đã ARCHIVED")
    void askQuestion_ForwardsRagEligibleDocumentIdsAndExcludesArchivedCitations() throws Exception {
        AIConversation conversation = new AIConversation(studentId, "Quy chế", null);
        UUID activeDocId = UUID.randomUUID();
        UUID archivedDocId = UUID.randomUUID();

        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                conversation.getId(), studentId.toString(), "AI"
        )).thenReturn(Optional.of(conversation));
        when(documentRepository.findRagEligibleDocumentIds()).thenReturn(List.of(activeDocId));

        Document archivedDoc = new Document(
                activeAdvisor,
                "Quy chế cũ đã thu hồi",
                null,
                true,
                "old_qc.pdf",
                "documents/old_qc.pdf",
                "application/pdf",
                1024L,
                "c".repeat(64)
        );
        setField(archivedDoc, "id", archivedDocId);
        archivedDoc.completeRagIndexing();
        archivedDoc.archive();
        when(documentRepository.findAllById(anyCollection())).thenReturn(List.of(archivedDoc));

        AiServiceSourceCitation staleCitation = new AiServiceSourceCitation(
                archivedDocId.toString(),
                "old_qc.pdf",
                1,
                0,
                0,
                20,
                0.88,
                "Trích dẫn cũ",
                "Nội dung cũ"
        );
        when(aiServiceClient.chat(any(AiServiceChatRequest.class))).thenReturn(
                new AiServiceChatResponse("Hỏi quy chế?", "Trả lời mới.", List.of(staleCitation))
        );

        AiChatbotTurnResponse turn = aiChatbotService.askQuestion(
                studentId,
                conversation.getId(),
                new SendAiMessageRequest("Hỏi quy chế?", 5, null)
        );

        ArgumentCaptor<AiServiceChatRequest> requestCaptor = ArgumentCaptor.forClass(AiServiceChatRequest.class);
        verify(aiServiceClient).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().allowedDocumentIds()).containsExactly(activeDocId.toString());
        assertThat(turn.aiMessage().citations()).isEmpty();
    }

    @Test
    @DisplayName("Hội thoại cũ vẫn giữ nguyên hiển thị thông tin citation của tài liệu đã ARCHIVED")
    void getMessages_ArchivedDocumentCitationRemainsVisibleInHistory() throws Exception {
        AIConversation conversation = new AIConversation(studentId, "Lịch sử cũ", null);
        when(accountRepository.findById(studentId)).thenReturn(Optional.of(activeStudent));
        when(conversationRepository.findByIdAndStudentIdAndConversationTypeAndDeletedAtIsNull(
                conversation.getId(), studentId.toString(), "AI"
        )).thenReturn(Optional.of(conversation));

        Document archivedDoc = new Document(
                activeAdvisor,
                "Quy chế học vụ 2024 (đã lưu trữ)",
                null,
                false,
                "qc_2024.pdf",
                "documents/qc_2024.pdf",
                "application/pdf",
                1024L,
                "d".repeat(64)
        );
        setField(archivedDoc, "id", documentId);
        archivedDoc.completeRagIndexing();
        archivedDoc.archive();
        when(documentRepository.findAllById(anyCollection())).thenReturn(List.of(archivedDoc));

        Citation oldCitation = new Citation(documentId, "qc_2024.pdf", 4, "chunk_2", "Trích dẫn đã lưu", 0.91);
        AIMessage aiMsg = new AIMessage(conversation.getId(), "Trả lời trước đây", List.of(oldCitation));
        when(messageRepository.findByConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                eq(conversation.getId()), any(Pageable.class)
        )).thenReturn(new SliceImpl<>(List.of(aiMsg), Pageable.ofSize(30), false));

        AiChatbotHistoryResponse history = aiChatbotService.getMessages(studentId, conversation.getId(), 0, 30);

        assertThat(history.messages()).hasSize(1);
        assertThat(history.messages().getFirst().citations()).hasSize(1);
        assertThat(history.messages().getFirst().citations().getFirst().sourceTitle())
                .isEqualTo("Quy chế học vụ 2024 (đã lưu trữ)");
        assertThat(history.messages().getFirst().citations().getFirst().quotedContent())
                .isEqualTo("Trích dẫn đã lưu");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
