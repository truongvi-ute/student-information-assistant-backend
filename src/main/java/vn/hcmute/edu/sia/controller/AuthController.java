package vn.hcmute.edu.sia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import vn.hcmute.edu.sia.dto.request.RegisterRequest;
import vn.hcmute.edu.sia.dto.request.VerifyRegisterOtpRequest;
import vn.hcmute.edu.sia.dto.response.OtpResendCooldownResponse;
import vn.hcmute.edu.sia.service.RegisterService;
import vn.hcmute.edu.sia.service.LoginService;
import vn.hcmute.edu.sia.dto.request.LoginRequest;
import vn.hcmute.edu.sia.dto.response.LoginResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private final RegisterService registerService;
        private final LoginService loginService;

        public AuthController(RegisterService registerService, LoginService loginService) {
                this.registerService = registerService;
                this.loginService = loginService;
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
}