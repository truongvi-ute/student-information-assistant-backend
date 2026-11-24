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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.DISCONNECT || command == StompCommand.UNSUBSCRIBE) {
            return message;
        }

        if (command != StompCommand.CONNECT && command != StompCommand.STOMP) {
            validateAuthorizedSession(accessor);
            String destination = accessor.getDestination();
            if (command == StompCommand.SEND) {
                if (destination == null || !destination.startsWith("/app/") || destination.contains("..")) {
                    throw new AccessDeniedException("Invalid STOMP SEND destination.");
                }
                return message;
            }
            if (command == StompCommand.SUBSCRIBE) {
                if (destination == null || !destination.startsWith("/user/queue/") || destination.contains("..")) {
                    throw new AccessDeniedException("Invalid STOMP SUBSCRIBE destination.");
                }
                return message;
            }
            throw new AccessDeniedException("Unsupported STOMP command.");
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

        if (account.getRole() == AccountRole.ADVISOR && account.isPasswordChangeRequired()) {
            throw new AccessDeniedException(
                    "Password change is required before connecting to chat."
            );
        }

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

        accessor.setUser(authentication);

        return message;
    }

    private void validateAuthorizedSession(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccessDeniedException("Unauthenticated STOMP session.");
        }

        boolean isAdvisor = false;
        boolean isStudent = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADVISOR".equals(role)) {
                isAdvisor = true;
            } else if ("ROLE_STUDENT".equals(role)) {
                isStudent = true;
            }
        }

        if (!isAdvisor && !isStudent) {
            throw new AccessDeniedException("Only advisors and students can use chat.");
        }

        if (isAdvisor && principal.isPasswordChangeRequired()) {
            throw new AccessDeniedException("Password change is required before using chat.");
        }
    }
}
