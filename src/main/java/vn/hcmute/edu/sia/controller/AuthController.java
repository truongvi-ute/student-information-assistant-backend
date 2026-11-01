package vn.hcmute.edu.sia.controller;

import java.time.Duration;
import java.util.Arrays;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.request.ChangePasswordRequest;
import vn.hcmute.edu.sia.dto.request.ResetPasswordRequest;
import vn.hcmute.edu.sia.dto.request.VerifyForgotPasswordOtpRequest;
import vn.hcmute.edu.sia.dto.request.RegisterRequest;
import vn.hcmute.edu.sia.dto.request.ResendForgotPasswordOtpRequest;
import vn.hcmute.edu.sia.dto.request.VerifyRegisterOtpRequest;
import vn.hcmute.edu.sia.dto.request.ResendRegistrationOtpRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordOtpVerificationResponse;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;
import vn.hcmute.edu.sia.dto.response.PasswordChangedResponse;
import vn.hcmute.edu.sia.dto.response.PasswordResetResponse;
import vn.hcmute.edu.sia.dto.response.RegistrationOtpResponse;
import vn.hcmute.edu.sia.service.ForgotPasswordService;
import vn.hcmute.edu.sia.service.ChangePasswordService;
import vn.hcmute.edu.sia.service.RegisterService;
import vn.hcmute.edu.sia.service.LoginService;
import vn.hcmute.edu.sia.dto.request.LoginRequest;
import vn.hcmute.edu.sia.dto.response.LoginResponse;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.dto.response.AccessTokenRefreshResponse;
import vn.hcmute.edu.sia.dto.internal.IssuedTokens;
import vn.hcmute.edu.sia.dto.internal.LoginResult;
import vn.hcmute.edu.sia.service.RefreshTokenService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final RegisterService registerService;
        private final LoginService loginService;
        private final ForgotPasswordService forgotPasswordService;
        private final ChangePasswordService changePasswordService;
        private final RefreshTokenService refreshTokenService;
        private final String refreshCookieName;
        private final boolean refreshCookieSecure;
        private final String refreshCookieSameSite;
        private final String frontendOrigin;

        public AuthController(
                RegisterService registerService,
                LoginService loginService,
                ForgotPasswordService forgotPasswordService,
                ChangePasswordService changePasswordService,
                RefreshTokenService refreshTokenService,
                @Value("${app.auth.refresh-cookie-name}") String refreshCookieName,
                @Value("${app.auth.refresh-cookie-secure}") boolean refreshCookieSecure,
                @Value("${app.auth.refresh-cookie-same-site}") String refreshCookieSameSite,
                @Value("${app.frontend-origin}") String frontendOrigin
        ) {
                this.registerService = registerService;
                this.loginService = loginService;
                this.forgotPasswordService = forgotPasswordService;
                this.changePasswordService = changePasswordService;
                this.refreshTokenService = refreshTokenService;
                this.refreshCookieName = refreshCookieName;
                this.refreshCookieSecure = refreshCookieSecure;
                this.refreshCookieSameSite = refreshCookieSameSite;
                this.frontendOrigin = frontendOrigin;
                if (!"Lax".equalsIgnoreCase(refreshCookieSameSite)
                        && !"Strict".equalsIgnoreCase(refreshCookieSameSite)
                        && !"None".equalsIgnoreCase(refreshCookieSameSite)) {
                        throw new IllegalArgumentException("Refresh cookie SameSite must be Lax, Strict, or None.");
                }
                if ("None".equalsIgnoreCase(refreshCookieSameSite) && !refreshCookieSecure) {
                        throw new IllegalArgumentException("A SameSite=None refresh cookie must be Secure.");
                }
        }

        @PostMapping("/register")
        public ResponseEntity<RegistrationOtpResponse> register(
                @Valid @RequestBody RegisterRequest request) {
                RegistrationOtpResponse response = registerService.startRegistration(request);

                return ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(response);
        }

        @PostMapping ("/register/resend-otp")
        public ResponseEntity<RegistrationOtpResponse> resendRegistrationOtp(
                @Valid @RequestBody ResendRegistrationOtpRequest request){
                RegistrationOtpResponse response = registerService.resendRegistrationOtp(request.email());

                return ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(response);
        }

        @PostMapping("/register/verify-otp")
        public ResponseEntity<Void> verifyRegistrationOtp(
                @Valid @RequestBody VerifyRegisterOtpRequest request) {
                registerService.verifyRegistrationOtp(request.email(), request.otp());

                return ResponseEntity
                        .status(HttpStatus.CREATED)
                        .build();
        }

        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(
                @RequestHeader(value = "Origin", required = false) String origin,
                @Valid @RequestBody LoginRequest request) {
                requireTrustedOrigin(origin);
                LoginResult result = loginService.login(request);
                ResponseCookie cookie = refreshCookie(
                        result.tokens().refreshToken(),
                        result.tokens().refreshTokenMaxAge()
                );

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .header(HttpHeaders.SET_COOKIE, cookie.toString())
                        .body(result.response());
        }

        @PostMapping("/refresh")
        public ResponseEntity<AccessTokenRefreshResponse> refresh(
                @RequestHeader(value = "Origin", required = false) String origin,
                HttpServletRequest request
        ) {
                requireTrustedOrigin(origin);
                String refreshToken = readRefreshCookie(request);
                if (refreshToken == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired.");
                }

                IssuedTokens tokens = refreshTokenService.refresh(refreshToken);
                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, refreshCookie(
                                tokens.refreshToken(),
                                tokens.refreshTokenMaxAge()
                        ).toString())
                        .body(new AccessTokenRefreshResponse(tokens.accessToken()));
        }

        @PostMapping("/logout")
        public ResponseEntity<Void> logout(
                @RequestHeader(value = "Origin", required = false) String origin,
                HttpServletRequest request
        ) {
                requireTrustedOrigin(origin);
                String refreshToken = readRefreshCookie(request);
                if (refreshToken != null) {
                        refreshTokenService.revoke(refreshToken);
                }

                return ResponseEntity.noContent()
                        .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                        .build();
        }

        @PostMapping("/forgot-password")
        public ResponseEntity<ForgotPasswordResponse> forgotPassword(
                @Valid @RequestBody ForgotPasswordRequest request) {
                ForgotPasswordResponse response = forgotPasswordService.startForgotPassword(request);

                return ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(response);
        }
        @PostMapping("/forgot-password/resend-otp")
        public ResponseEntity<ForgotPasswordResponse> resendForgotPasswordOtp (
                @Valid @RequestBody ResendForgotPasswordOtpRequest request) {
                ForgotPasswordResponse response = forgotPasswordService.resendForgotPasswordOtp(request.email());
                return ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(response);
        }

        @PostMapping("/forgot-password/verify-otp")
        public ResponseEntity<ForgotPasswordOtpVerificationResponse> verifyForgotPasswordOtp(
                @Valid @RequestBody VerifyForgotPasswordOtpRequest request) {
                ForgotPasswordOtpVerificationResponse response = forgotPasswordService.verifyForgotPasswordOtp(request);

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(response);
        }

        @PostMapping("/reset-password")
        public ResponseEntity<PasswordResetResponse> resetPassword(
                @Valid @RequestBody ResetPasswordRequest request
        ) {
                PasswordResetResponse response =
                        forgotPasswordService.resetPassword(request);

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                        .body(response);
        }

        @PostMapping("/change-password")
        public ResponseEntity<PasswordChangedResponse> changePassword(
                @AuthenticationPrincipal AccountPrincipal principal,
                @Valid @RequestBody ChangePasswordRequest request
        ) {
                PasswordChangedResponse response = changePasswordService.changePassword(
                        principal.getAccountId(),
                        request
                );

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                        .body(response);
        }

        private String readRefreshCookie(HttpServletRequest request) {
                Cookie[] cookies = request.getCookies();
                if (cookies == null) {
                        return null;
                }
                return Arrays.stream(cookies)
                        .filter(cookie -> refreshCookieName.equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null);
        }

        private ResponseCookie refreshCookie(String value, Duration maxAge) {
                return ResponseCookie.from(refreshCookieName, value)
                        .httpOnly(true)
                        .secure(refreshCookieSecure)
                        .sameSite(refreshCookieSameSite)
                        .path("/api/auth")
                        .maxAge(maxAge)
                        .build();
        }

        private ResponseCookie expiredRefreshCookie() {
                return ResponseCookie.from(refreshCookieName, "")
                        .httpOnly(true)
                        .secure(refreshCookieSecure)
                        .sameSite(refreshCookieSameSite)
                        .path("/api/auth")
                        .maxAge(Duration.ZERO)
                        .build();
        }

        private void requireTrustedOrigin(String origin) {
                if (!frontendOrigin.equals(origin)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin is not allowed.");
                }
        }
}
