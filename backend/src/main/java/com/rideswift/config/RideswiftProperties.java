package com.rideswift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the ride flow. Binds {@code rideswift.matching|location|fare}.
 * ({@code rideswift.security.jwt} is bound separately by JwtProperties.)
 */
@ConfigurationProperties(prefix = "rideswift")
public record RideswiftProperties(
        Matching matching,
        Location location,
        Fare fare
) {
    public record Matching(double searchRadiusMeters, int candidateLimit) {
    }

    public record Location(long cacheTtlSeconds) {
    }

    public record Fare(double averageSpeedKmph) {
    }
}
