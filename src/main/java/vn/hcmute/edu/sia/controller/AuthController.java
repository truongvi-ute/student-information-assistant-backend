package vn.hcmute.edu.sia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import vn.hcmute.edu.sia.dto.request.ForgotPasswordRequest;
import vn.hcmute.edu.sia.dto.request.ResetPasswordRequest;
import vn.hcmute.edu.sia.dto.request.VerifyForgotPasswordOtpRequest;
import vn.hcmute.edu.sia.dto.request.RegisterRequest;
import vn.hcmute.edu.sia.dto.request.VerifyRegisterOtpRequest;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordOtpVerificationResponse;
import vn.hcmute.edu.sia.dto.response.ForgotPasswordResponse;
import vn.hcmute.edu.sia.dto.response.MessageResponse;
import vn.hcmute.edu.sia.dto.response.OtpResendCooldownResponse;
import vn.hcmute.edu.sia.service.ForgotPasswordService;
import vn.hcmute.edu.sia.service.RegisterService;
import vn.hcmute.edu.sia.service.LoginService;
import vn.hcmute.edu.sia.dto.request.LoginRequest;
import vn.hcmute.edu.sia.dto.response.LoginResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final RegisterService registerService;
        private final LoginService loginService;
        private final ForgotPasswordService forgotPasswordService;

        public AuthController(
                RegisterService registerService,
                LoginService loginService,
                ForgotPasswordService forgotPasswordService
        ) {
                this.registerService = registerService;
                this.loginService = loginService;
                this.forgotPasswordService = forgotPasswordService;
        }

        @PostMapping("/register")
        public ResponseEntity<OtpResendCooldownResponse> register(
                @Valid @RequestBody RegisterRequest request
        ) {
                OtpResendCooldownResponse response = registerService.startRegistration(request);

                return ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(response);
        }

        @PostMapping("/register/verify")
        public ResponseEntity<Void> verifyRegistration(
                @Valid @RequestBody VerifyRegisterOtpRequest request
        ) {
                registerService.verifyRegistration(
                        request.email(),
                        request.otp()
                );

                return ResponseEntity
                        .status(HttpStatus.CREATED)
                        .build();
        }
        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(
                @Valid @RequestBody LoginRequest request
                ) {
                LoginResponse response = loginService.login(request);

                return ResponseEntity.ok(response);
        }

        @PostMapping("/forgot-password")
        public ResponseEntity<ForgotPasswordResponse> forgotPassword(
                @Valid @RequestBody ForgotPasswordRequest request
        ) {
                ForgotPasswordResponse response =
                        forgotPasswordService.startForgotPassword(request);

                return ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body(response);
        }

        @PostMapping("/forgot-password/verify")
        public ResponseEntity<ForgotPasswordOtpVerificationResponse> verifyForgotPasswordOtp(
                @Valid @RequestBody VerifyForgotPasswordOtpRequest request
        ) {
                ForgotPasswordOtpVerificationResponse response =
                        forgotPasswordService.verifyForgotPasswordOtp(request);

                return ResponseEntity.ok(response);
        }

        @PostMapping("/reset-password")
        public ResponseEntity<MessageResponse> resetPassword(
                @Valid @RequestBody ResetPasswordRequest request
        ) {
                MessageResponse response =
                        forgotPasswordService.resetPassword(request);

                return ResponseEntity.ok(response);
        }
}
