package com.rideswift.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rideswift.security.jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {
}
