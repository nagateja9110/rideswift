package com.rideswift.service;

import com.rideswift.dto.request.FirebaseAuthRequest;
import com.rideswift.dto.request.LoginRequest;
import com.rideswift.dto.request.OtpRequestRequest;
import com.rideswift.dto.request.OtpVerifyRequest;
import com.rideswift.dto.request.RefreshTokenRequest;
import com.rideswift.dto.request.RegisterRequest;
import com.rideswift.dto.response.AuthResponse;
import com.rideswift.dto.response.OtpChallengeResponse;
import com.rideswift.dto.response.UserResponse;
import com.rideswift.exception.ConflictException;
import com.rideswift.model.RefreshToken;
import com.rideswift.model.Role;
import com.rideswift.model.User;
import com.rideswift.repository.RefreshTokenRepository;
import com.rideswift.repository.UserRepository;
import com.rideswift.security.FirebaseTokenVerifier;
import com.rideswift.security.JwtService;
import com.rideswift.sms.SmsSender;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final String OTP_KEY_PREFIX = "otp:";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redis;
    private final SmsSender smsSender;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final int otpLength;
    private final long otpTtlSeconds;
    private final boolean otpExposeCode;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       StringRedisTemplate redis,
                       SmsSender smsSender,
                       FirebaseTokenVerifier firebaseTokenVerifier,
                       @Value("${rideswift.otp.length:6}") int otpLength,
                       @Value("${rideswift.otp.ttl-seconds:300}") long otpTtlSeconds,
                       @Value("${rideswift.otp.expose-code:true}") boolean otpExposeCode) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.redis = redis;
        this.smsSender = smsSender;
        this.firebaseTokenVerifier = firebaseTokenVerifier;
        this.otpLength = otpLength;
        this.otpTtlSeconds = otpTtlSeconds;
        this.otpExposeCode = otpExposeCode;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already registered");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new ConflictException("Phone number already registered");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .hashedPassword(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        user = userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        return issueTokens(user);
    }

    /** Phone login step 1: generate a one-time code, cache it (short TTL) and SMS it. */
    public OtpChallengeResponse requestOtp(OtpRequestRequest request) {
        String phone = request.phone().trim();
        String code = randomCode();
        redis.opsForValue().set(OTP_KEY_PREFIX + phone, code, Duration.ofSeconds(otpTtlSeconds));
        smsSender.send(phone, "Your RideSwift verification code is " + code
                + ". It expires in " + (otpTtlSeconds / 60) + " minutes.");
        return new OtpChallengeResponse(phone,
                "A verification code has been sent to " + phone,
                otpExposeCode ? code : null);   // dev convenience only
    }

    /** Phone login step 2: verify the code, then sign in (creating the account on first use). */
    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        String phone = request.phone().trim();
        String key = OTP_KEY_PREFIX + phone;
        String expected = redis.opsForValue().get(key);
        if (expected == null || !expected.equals(request.code().trim())) {
            throw new BadCredentialsException("Invalid or expired code");
        }
        redis.delete(key);   // single-use

        User user = userRepository.findByPhone(phone).orElseGet(() -> registerPhoneUser(phone, request.name()));
        return issueTokens(user);
    }

    /** Firebase phone login: verify Google's ID token, then sign in by phone number. */
    @Transactional
    public AuthResponse loginWithFirebase(FirebaseAuthRequest request) {
        FirebaseTokenVerifier.FirebaseUser verified = firebaseTokenVerifier.verify(request.idToken());
        String phone = verified.phoneNumber();
        if (phone == null || phone.isBlank()) {
            throw new BadCredentialsException("Firebase token has no phone number");
        }
        String name = request.name() != null && !request.name().isBlank() ? request.name() : verified.name();
        User user = userRepository.findByPhone(phone).orElseGet(() -> registerPhoneUser(phone, name));
        return issueTokens(user);
    }

    /** Creates a fresh passenger account for a first-time phone login. */
    private User registerPhoneUser(String phone, String name) {
        String digits = phone.replaceAll("[^0-9]", "");
        String displayName = (name != null && !name.isBlank())
                ? name.trim()
                : "Rider " + digits.substring(Math.max(0, digits.length() - 4));
        User user = User.builder()
                .name(displayName)
                .email("otp+" + digits + "@phone.rideswift.local")
                .phone(phone)
                // No password for OTP accounts — set an unusable random hash.
                .hashedPassword(passwordEncoder.encode(jwtService.generateRefreshTokenValue()))
                .role(Role.PASSENGER)
                .build();
        return userRepository.save(user);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(OTP_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hash = jwtService.hashRefreshToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        // Rotate: revoke the presented token and issue a fresh pair.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String hash = jwtService.hashRefreshToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRefreshTokenValue();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashRefreshToken(rawRefresh))
                .expiresAt(Instant.now().plusSeconds(jwtService.refreshTokenTtlSeconds()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(
                accessToken,
                rawRefresh,
                jwtService.accessTokenTtlSeconds(),
                UserResponse.from(user));
    }
}
