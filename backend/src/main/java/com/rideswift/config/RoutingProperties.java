package com.rideswift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the OSRM routing engine. Binds {@code rideswift.routing}.
 * {@code baseUrl} points at an OSRM HTTP server (self-hosted container in prod,
 * the public demo server for local dev). When OSRM is unreachable the fare/route
 * paths fall back to a straight-line (haversine) estimate, so {@code enabled=false}
 * or a down server degrades gracefully rather than failing the booking.
 */
@ConfigurationProperties(prefix = "rideswift.routing")
public record RoutingProperties(
        boolean enabled,
        String baseUrl,
        String profile,
        long cacheTtlSeconds,
        long timeoutMillis
) {
}
