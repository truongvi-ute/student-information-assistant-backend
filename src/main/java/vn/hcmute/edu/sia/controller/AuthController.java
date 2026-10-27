package vn.hcmute.edu.sia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

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
import vn.hcmute.edu.sia.dto.response.MessageResponse;
import vn.hcmute.edu.sia.dto.response.RegistrationOtpResponse;
import vn.hcmute.edu.sia.service.ForgotPasswordService;
import vn.hcmute.edu.sia.service.ChangePasswordService;
import vn.hcmute.edu.sia.service.RegisterService;
import vn.hcmute.edu.sia.service.LoginService;
import vn.hcmute.edu.sia.dto.request.LoginRequest;
import vn.hcmute.edu.sia.dto.response.LoginResponse;
import vn.hcmute.edu.sia.security.AccountPrincipal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final RegisterService registerService;
        private final LoginService loginService;
        private final ForgotPasswordService forgotPasswordService;
        private final ChangePasswordService changePasswordService;

        public AuthController(
                RegisterService registerService,
                LoginService loginService,
                ForgotPasswordService forgotPasswordService,
                ChangePasswordService changePasswordService
        ) {
                this.registerService = registerService;
                this.loginService = loginService;
                this.forgotPasswordService = forgotPasswordService;
                this.changePasswordService = changePasswordService;
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
                @Valid @RequestBody LoginRequest request) {
                LoginResponse response = loginService.login(request);

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(response);
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
        public ResponseEntity<MessageResponse> resetPassword(
                @Valid @RequestBody ResetPasswordRequest request
        ) {
                MessageResponse response =
                        forgotPasswordService.resetPassword(request);

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(response);
        }

        @PostMapping("/change-password")
        public ResponseEntity<MessageResponse> changePassword(
                @AuthenticationPrincipal AccountPrincipal principal,
                @Valid @RequestBody ChangePasswordRequest request
        ) {
                MessageResponse response = changePasswordService.changePassword(
                        principal.getAccountId(),
                        request
                );

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(response);
        }
}
