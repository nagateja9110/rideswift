package com.rideswift.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideswift.model.Role;
import com.rideswift.model.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "Y2hhbmdlLW1lLXRoaXMtaXMtYS1kZXYtb25seS1zZWNyZXQta2V5LTEyMzQ1Ng==",
            900,
            604800);
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void generatesAndValidatesAccessToken() {
        User user = User.builder()
                .name("Ada")
                .email("ada@example.com")
                .phone("+15551234567")
                .hashedPassword("hash")
                .role(Role.PASSENGER)
                .build();
        user.setId(UUID.randomUUID());

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
    }

    @Test
    void rejectsTamperedToken() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void refreshTokenHashIsStableAndOpaque() {
        String raw = jwtService.generateRefreshTokenValue();
        assertThat(raw).isNotBlank();
        assertThat(jwtService.hashRefreshToken(raw)).isEqualTo(jwtService.hashRefreshToken(raw));
        assertThat(jwtService.hashRefreshToken(raw)).isNotEqualTo(raw);
    }
}
