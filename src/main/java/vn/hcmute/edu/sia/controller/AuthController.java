package vn.hcmute.edu.sia.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import vn.hcmute.edu.sia.dto.request.RegisterRequest;
import vn.hcmute.edu.sia.dto.request.VerifyRegisterOtpRequest;
import vn.hcmute.edu.sia.service.RegisterService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterService registerService;

    public AuthController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        registerService.startRegistration(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .build();
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
}