package vn.hcmute.edu.sia.security;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.repository.AccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthenticationInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private AccountRepository accountRepository;
    @Mock private MessageChannel channel;

    private StompAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthenticationInterceptor(jwtService, accountRepository);
    }

    @Test
    @DisplayName("Cho phép CONNECT với Bearer token hợp lệ của Student")
    void preSend_ConnectValidStudentToken_SetsPrincipal() {
        UUID userId = UUID.randomUUID();
        Account student = mockAccount(userId, AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        when(jwtService.extractUserId("valid-token")).thenReturn(userId);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(student));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(msg, channel);

        assertThat(result).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    @DisplayName("Chặn CONNECT khi tài khoản bị khóa hoặc Advisor chưa đổi mật khẩu")
    void preSend_ConnectBlockedOrPasswordChangeRequired_ThrowsAccessDenied() {
        UUID blockedId = UUID.randomUUID();
        Account blocked = mockAccount(blockedId, AccountRole.STUDENT, AccountAccessStatus.BLOCKED, false);
        when(jwtService.extractUserId("blocked-token")).thenReturn(blockedId);
        when(accountRepository.findById(blockedId)).thenReturn(Optional.of(blocked));

        StompHeaderAccessor blockedAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        blockedAccessor.setNativeHeader("Authorization", "Bearer blocked-token");
        Message<byte[]> blockedMsg = MessageBuilder.createMessage(new byte[0], blockedAccessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(blockedMsg, channel))
                .isInstanceOf(AccessDeniedException.class);

        UUID advisorId = UUID.randomUUID();
        Account advisor = mockAccount(advisorId, AccountRole.ADVISOR, AccountAccessStatus.ACTIVE, true);
        when(jwtService.extractUserId("advisor-token")).thenReturn(advisorId);
        when(accountRepository.findById(advisorId)).thenReturn(Optional.of(advisor));

        StompHeaderAccessor advisorAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        advisorAccessor.setNativeHeader("Authorization", "Bearer advisor-token");
        Message<byte[]> advisorMsg = MessageBuilder.createMessage(new byte[0], advisorAccessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(advisorMsg, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SEND chỉ cho phép tới /app/... với phiên đã xác thực")
    void preSend_SendDestinationAuthorization() {
        Account student = mockAccount(UUID.randomUUID(), AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        UsernamePasswordAuthenticationToken auth = authFor(student);

        Message<byte[]> allowed = frame(StompCommand.SEND, "/app/sendMessage", auth);
        assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/sendMessage", null), channel))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/queue/messages", auth), channel))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/chat", auth), channel))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/../topic/chat", auth), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SUBSCRIBE chỉ cho phép tới /user/queue/... với phiên đã xác thực")
    void preSend_SubscribeDestinationAuthorization() {
        Account advisor = mockAccount(UUID.randomUUID(), AccountRole.ADVISOR, AccountAccessStatus.ACTIVE, false);
        UsernamePasswordAuthenticationToken auth = authFor(advisor);

        Message<byte[]> allowed = frame(StompCommand.SUBSCRIBE, "/user/queue/messages", auth);
        assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages", null), channel))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/queue/messages-user123", auth), channel))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/messages", auth), channel))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/../topic", auth), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Cho phép STOMP command thay cho CONNECT khi Bearer token hợp lệ")
    void preSend_StompFrameValidToken_SetsPrincipal() {
        UUID userId = UUID.randomUUID();
        Account student = mockAccount(userId, AccountRole.STUDENT, AccountAccessStatus.ACTIVE, false);
        when(jwtService.extractUserId("stomp-token")).thenReturn(userId);
        when(accountRepository.findById(userId)).thenReturn(Optional.of(student));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.STOMP);
        accessor.setNativeHeader("Authorization", "Bearer stomp-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(msg, channel);

        assertThat(result).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
    }

    @Test
    @DisplayName("Cờ passwordChangeRequired trên session chỉ chặn Advisor, vẫn cho Student SEND và SUBSCRIBE")
    void preSend_PasswordChangeRequiredOnActiveSession_OnlyBlocksAdvisor() {
        Account student = mockAccount(UUID.randomUUID(), AccountRole.STUDENT, AccountAccessStatus.ACTIVE, true);
        UsernamePasswordAuthenticationToken studentAuth = authFor(student);

        Message<byte[]> studentSub = frame(StompCommand.SUBSCRIBE, "/user/queue/messages", studentAuth);
        Message<byte[]> studentSend = frame(StompCommand.SEND, "/app/sendMessage", studentAuth);
        assertThat(interceptor.preSend(studentSub, channel)).isSameAs(studentSub);
        assertThat(interceptor.preSend(studentSend, channel)).isSameAs(studentSend);

        Account advisor = mockAccount(UUID.randomUUID(), AccountRole.ADVISOR, AccountAccessStatus.ACTIVE, true);
        UsernamePasswordAuthenticationToken advisorAuth = authFor(advisor);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/messages", advisorAuth), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/sendMessage", advisorAuth), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Message<byte[]> frame(
            StompCommand command,
            String destination,
            UsernamePasswordAuthenticationToken user
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static UsernamePasswordAuthenticationToken authFor(Account account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static Account mockAccount(
            UUID id,
            AccountRole role,
            AccountAccessStatus status,
            boolean passwordChangeRequired
    ) {
        Account account = mock(Account.class);
        lenient().when(account.getId()).thenReturn(id);
        lenient().when(account.getRole()).thenReturn(role);
        lenient().when(account.getAccessStatus()).thenReturn(status);
        lenient().when(account.getEmail()).thenReturn("user@hcmute.edu.vn");
        lenient().when(account.getPasswordHash()).thenReturn("hash");
        lenient().when(account.isPasswordChangeRequired()).thenReturn(passwordChangeRequired);
        return account;
    }
}
