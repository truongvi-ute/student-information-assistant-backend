package vn.hcmute.edu.sia.controller;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.dto.response.ChatErrorEvent;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.UserMessage;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.ChatService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private ChatService chatService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private ChatController controller;

    private UUID senderId;
    private UUID recipientId;
    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(senderId);
        when(account.getEmail()).thenReturn("student@hcmute.edu.vn");
        when(account.getPasswordHash()).thenReturn("hash");
        when(account.getRole()).thenReturn(AccountRole.STUDENT);
        when(account.isPasswordChangeRequired()).thenReturn(false);

        AccountPrincipal accountPrincipal = new AccountPrincipal(account);
        principal = new UsernamePasswordAuthenticationToken(accountPrincipal, null, accountPrincipal.getAuthorities());
    }

    @Test
    @DisplayName("Gửi tin nhắn hợp lệ gọi ChatService và không gửi lỗi")
    void sendMessage_ValidRequest_CallsChatService() {
        SendChatMessageRequest request = new SendChatMessageRequest(recipientId, "Xin chào thầy", "cmsg-1");

        controller.sendMessage(request, principal);

        verify(chatService).sendPrivateMessage(senderId, request);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Thiếu recipientId trả về ChatErrorEvent trên /queue/errors")
    void sendMessage_MissingRecipientId_SendsErrorEvent() {
        SendChatMessageRequest request = new SendChatMessageRequest(null, "Xin chào", "cmsg-2");

        controller.sendMessage(request, principal);

        verifyNoInteractions(chatService);
        ArgumentCaptor<ChatErrorEvent> captor = ArgumentCaptor.forClass(ChatErrorEvent.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("student@hcmute.edu.vn"),
                eq("/queue/errors"),
                captor.capture()
        );
        ChatErrorEvent event = captor.getValue();
        assertThat(event.clientMessageId()).isEqualTo("cmsg-2");
        assertThat(event.recipientId()).isNull();
        assertThat(event.message()).isEqualTo("Recipient ID must not be null");
        assertThat(event.type()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("Cắt gọn clientMessageId quá dài trước khi phát ChatErrorEvent")
    void sendMessage_OversizedClientMessageId_TruncatesInErrorEvent() {
        String oversizedClientId = "x".repeat(150);
        SendChatMessageRequest request = new SendChatMessageRequest(recipientId, "Xin chào", oversizedClientId);
        doThrow(new IllegalArgumentException("Client message ID must not exceed 128 characters"))
                .when(chatService).sendPrivateMessage(senderId, request);

        controller.sendMessage(request, principal);

        ArgumentCaptor<ChatErrorEvent> captor = ArgumentCaptor.forClass(ChatErrorEvent.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("student@hcmute.edu.vn"),
                eq("/queue/errors"),
                captor.capture()
        );
        ChatErrorEvent event = captor.getValue();
        assertThat(event.clientMessageId()).hasSize(UserMessage.MAX_CLIENT_MESSAGE_ID_LENGTH);
        assertThat(event.message()).contains("128 characters");
    }

    @Test
    @DisplayName("Ngoại lệ hệ thống bất ngờ vẫn trả thông báo an toàn trên /queue/errors")
    void sendMessage_UnexpectedException_SendsSafeGenericErrorEvent() {
        SendChatMessageRequest request = new SendChatMessageRequest(recipientId, "Xin chào", "cmsg-err");
        doThrow(new RuntimeException("Mongo timed out"))
                .when(chatService).sendPrivateMessage(senderId, request);

        controller.sendMessage(request, principal);

        ArgumentCaptor<ChatErrorEvent> captor = ArgumentCaptor.forClass(ChatErrorEvent.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("student@hcmute.edu.vn"),
                eq("/queue/errors"),
                captor.capture()
        );
        ChatErrorEvent event = captor.getValue();
        assertThat(event.clientMessageId()).isEqualTo("cmsg-err");
        assertThat(event.recipientId()).isEqualTo(recipientId.toString());
        assertThat(event.message()).isEqualTo("Không thể gửi tin nhắn lúc này, vui lòng thử lại.");
    }
}
