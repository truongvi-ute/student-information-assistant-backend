package vn.hcmute.edu.sia.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.repository.AccountRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountRepository accountRepository;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        AccountRepository accountRepository
    ) {
        this.jwtService = jwtService;
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
            request.getHeader("Authorization");

        if (
            authorizationHeader == null ||
            !authorizationHeader.startsWith(BEARER_PREFIX)
        ) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(
            BEARER_PREFIX.length()
        );

        try {
            authenticate(token, request);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(
        String token,
        HttpServletRequest request
    ) {
        if (
            SecurityContextHolder
                .getContext()
                .getAuthentication() != null
        ) {
            return;
        }

        UUID accountId = jwtService.extractUserId(token);

        Account account = accountRepository
            .findById(accountId)
            .orElse(null);

        if (account == null) {
            return;
        }

        if (
            account.getAccessStatus()
                == AccountAccessStatus.BLOCKED
        ) {
            return;
        }

        AccountPrincipal principal =
            new AccountPrincipal(account);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
            );

        authentication.setDetails(
            new WebAuthenticationDetailsSource()
                .buildDetails(request)
        );

        SecurityContextHolder
            .getContext()
            .setAuthentication(authentication);
    }
}