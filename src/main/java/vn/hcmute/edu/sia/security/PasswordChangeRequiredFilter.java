package vn.hcmute.edu.sia.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import vn.hcmute.edu.sia.dto.response.ApiErrorResponse;

public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_ADVISOR = "ROLE_ADVISOR";
    private static final String CHANGE_PASSWORD_PATH = "/api/auth/change-password";

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/register/resend-otp",
            "/api/auth/register/verify-otp",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            CHANGE_PASSWORD_PATH,
            "/api/auth/forgot-password",
            "/api/auth/forgot-password/resend-otp",
            "/api/auth/forgot-password/verify-otp",
            "/api/auth/reset-password"
    );

    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (request.getMethod().equalsIgnoreCase("OPTIONS")
                || !isPendingAdminOrAdvisor(authentication)
                || isAllowedEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        HttpStatus.FORBIDDEN.value(),
                        HttpStatus.FORBIDDEN.getReasonPhrase(),
                        "You must change your password before accessing this resource.",
                        OffsetDateTime.now()
                )
        );
    }

    private boolean isPendingAdminOrAdvisor(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)
                || !principal.isPasswordChangeRequired()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> ROLE_ADMIN.equals(authority) || ROLE_ADVISOR.equals(authority));
    }

    private boolean isAllowedEndpoint(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && ALLOWED_PATHS.contains(request.getServletPath());
    }
}
