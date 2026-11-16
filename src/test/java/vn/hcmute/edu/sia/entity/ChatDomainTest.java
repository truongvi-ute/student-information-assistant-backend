package vn.hcmute.edu.sia.entity;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.MessageSenderType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatDomainTest {

    @Test
    @DisplayName("Tạo DirectConversation hợp lệ và xóa mềm thành công")
    void createDirectConversation_Success() {
        UUID studentId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        DirectConversation conv = new DirectConversation(studentId, advisorId);

        assertThat(conv.getId()).isNotBlank();
        assertThat(conv.getStudentId()).isEqualTo(studentId.toString());
        assertThat(conv.getAdvisorId()).isEqualTo(advisorId.toString());
        assertThat(conv.getConversationType()).isEqualTo("ADVISOR");
        assertThat(conv.getStatus()).isEqualTo("ACTIVE");
        assertThat(conv.getDeletedAt()).isNull();

        conv.softDelete();
        assertThat(conv.getDeletedAt()).isNotNull();
        assertThat(conv.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Tạo DirectConversation thất bại khi studentId hoặc advisorId không hợp lệ")
    void createDirectConversation_InvalidIds_ThrowsException() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> new DirectConversation(null, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Student ID must not be null");

        assertThatThrownBy(() -> new DirectConversation(userId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Advisor ID must not be null");

        assertThatThrownBy(() -> new DirectConversation(userId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Student and advisor must be different accounts");
    }

    @Test
    @DisplayName("Tạo AIConversation gán tiêu đề mặc định khi trống và cho phép cập nhật tiêu đề")
    void createAIConversation_Success() {
        UUID studentId = UUID.randomUUID();
        UUID majorId = UUID.randomUUID();

        AIConversation conv = new AIConversation(studentId, "   ", majorId);

        assertThat(conv.getConversationType()).isEqualTo("AI");
        assertThat(conv.getTitle()).isEqualTo(AIConversation.DEFAULT_TITLE);
        assertThat(conv.getScopeMajorId()).isEqualTo(majorId.toString());

        conv.updateTitle("Hỏi về điều kiện tốt nghiệp");
        assertThat(conv.getTitle()).isEqualTo("Hỏi về điều kiện tốt nghiệp");
    }

    @Test
    @DisplayName("Tạo UserMessage thành công cho STUDENT và ADVISOR")
    void createUserMessage_Success() {
        UUID studentId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        UserMessage studentMsg = new UserMessage("conv-1", studentId, AccountRole.STUDENT, "  Em chào thầy ạ  ");
        UserMessage advisorMsg = new UserMessage("conv-1", advisorId, AccountRole.ADVISOR, "Chào em");

        assertThat(studentMsg.getSenderUserId()).isEqualTo(studentId.toString());
        assertThat(studentMsg.getSenderType()).isEqualTo(MessageSenderType.STUDENT);
        assertThat(studentMsg.getContent()).isEqualTo("Em chào thầy ạ");
        assertThat(advisorMsg.getSenderType()).isEqualTo(MessageSenderType.ADVISOR);
    }

    @Test
    @DisplayName("Tạo UserMessage thất bại khi senderId null, role ADMIN hoặc vượt 2000 ký tự")
    void createUserMessage_InvalidInputs_ThrowsException() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> new UserMessage("conv-1", null, AccountRole.STUDENT, "Hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sender ID must not be null");

        assertThatThrownBy(() -> new UserMessage("conv-1", userId, AccountRole.ADMIN, "Hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only students and advisors can send user messages");

        assertThatThrownBy(() -> new UserMessage("conv-1", userId, AccountRole.STUDENT, "a".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000 characters");
    }

    @Test
    @DisplayName("Tạo AIMessage kèm Citation và xóa mềm tin nhắn")
    void createAIMessage_WithCitationsAndDelete_Success() {
        UUID docId = UUID.randomUUID();
        Citation citation = new Citation(
                docId,
                "quy_che_2026.pdf",
                4,
                "chunk_2",
                "Sinh viên tích lũy đủ tín chỉ...",
                0.91
        );

        String longAiAnswer = "a".repeat(3500);
        AIMessage aiMessage = new AIMessage("conv-ai-1", longAiAnswer, List.of(citation), "gemini-2.5-flash");

        assertThat(aiMessage.getSenderType()).isEqualTo(MessageSenderType.AI);
        assertThat(aiMessage.getCitations()).hasSize(1);
        assertThat(aiMessage.getCitations().getFirst().getDocumentId()).isEqualTo(docId.toString());
        assertThat(aiMessage.getCitations().getFirst().getSourceTitle()).isEqualTo("quy_che_2026.pdf");
        assertThat(aiMessage.getModelName()).isEqualTo("gemini-2.5-flash");
        assertThat(aiMessage.getDeletedAt()).isNull();

        aiMessage.deleteMessage();
        assertThat(aiMessage.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Tạo Citation thất bại khi sourceTitle trống hoặc pageNumber không dương")
    void createCitation_Invalid_ThrowsException() {
        assertThatThrownBy(() -> new Citation(null, "  ", 1, "c0", "snippet", 0.8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source title must not be blank");

        assertThatThrownBy(() -> new Citation(null, "doc.pdf", 0, "c0", "snippet", 0.8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page number must be positive");
    }
}
