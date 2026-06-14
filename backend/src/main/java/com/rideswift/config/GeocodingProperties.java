package com.rideswift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the Nominatim (OpenStreetMap) geocoding proxy. Binds
 * {@code rideswift.geocoding}. Nominatim's usage policy requires a descriptive
 * User-Agent and asks callers to cache aggressively and keep request rates low —
 * hence the Redis-backed result cache in {@code GeocodingService}.
 */
@ConfigurationProperties(prefix = "rideswift.geocoding")
public record GeocodingProperties(
        String baseUrl,
        String userAgent,
        int limit,
        long cacheTtlSeconds,
        long timeoutMillis
) {
}
