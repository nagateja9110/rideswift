package com.rideswift.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Tracks whether a driver is being actively controlled by a real client (an open
 * driver dashboard pushing GPS/location). The server-side fleet simulation yields
 * to such drivers so a human driver app always wins over the simulator.
 *
 * Backed by a short-TTL Redis key refreshed on every client location push; when
 * the dashboard closes the key lapses and the simulator takes the driver back.
 */
@Service
public class DriverPresenceService {

    private static final String KEY_PREFIX = "driver:client-active:";

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public DriverPresenceService(StringRedisTemplate redis,
                                 @Value("${rideswift.fleet.presence-ttl-seconds:12}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    /** Marks a driver as controlled by a live client for the next {@code ttlSeconds}. */
    public void markClientActive(UUID driverId) {
        try {
            redis.opsForValue().set(KEY_PREFIX + driverId, "1", Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ignored) {
            // best-effort; if Redis is down the simulator simply also runs this driver
        }
    }

    public boolean isClientActive(UUID driverId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + driverId));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
