package com.rideswift.controller;

import com.rideswift.dto.request.FirebaseAuthRequest;
import com.rideswift.dto.request.LoginRequest;
import com.rideswift.dto.request.OtpRequestRequest;
import com.rideswift.dto.request.OtpVerifyRequest;
import com.rideswift.dto.request.RefreshTokenRequest;
import com.rideswift.dto.request.RegisterRequest;
import com.rideswift.dto.response.AuthResponse;
import com.rideswift.dto.response.OtpChallengeResponse;
import com.rideswift.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Phone login step 1 — send an OTP. */
    @PostMapping("/otp/request")
    public ResponseEntity<OtpChallengeResponse> requestOtp(@Valid @RequestBody OtpRequestRequest request) {
        return ResponseEntity.ok(authService.requestOtp(request));
    }

    /** Phone login step 2 — verify the OTP and sign in. */
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    /** Firebase phone login — exchange a verified Firebase ID token for a session. */
    @PostMapping("/firebase")
    public ResponseEntity<AuthResponse> firebase(@Valid @RequestBody FirebaseAuthRequest request) {
        return ResponseEntity.ok(authService.loginWithFirebase(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
