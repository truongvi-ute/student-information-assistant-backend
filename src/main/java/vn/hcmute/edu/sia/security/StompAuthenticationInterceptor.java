package vn.hcmute.edu.sia.security;

import java.util.UUID;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.repository.AccountRepository;

@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountRepository accountRepository;

    public StompAuthenticationInterceptor(
            JwtService jwtService,
            AccountRepository accountRepository
    ) {
        this.jwtService = jwtService;
        this.accountRepository = accountRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        // Xác thực lúc client mở phiên STOMP.
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization =
                accessor.getFirstNativeHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Missing bearer token.");
        }

        String token = authorization.substring(BEARER_PREFIX.length());

        Account account;
        try {
            UUID accountId = jwtService.extractUserId(token);
            account = accountRepository.findById(accountId)
                    .orElseThrow(() ->
                            new AccessDeniedException("Account not found.")
                    );
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid bearer token.");
        }

        if (account.getAccessStatus() == AccountAccessStatus.BLOCKED) {
            throw new AccessDeniedException("Account is blocked.");
        }

        // Chat này chỉ dành cho advisor và student.
        if (account.getRole() != AccountRole.ADVISOR
                && account.getRole() != AccountRole.STUDENT) {
            throw new AccessDeniedException(
                    "Only advisors and students can use chat."
            );
        }

        AccountPrincipal principal = new AccountPrincipal(account);

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        // Gắn người dùng đã xác thực vào phiên WebSocket.
        accessor.setUser(authentication);

        return message;
    }
}