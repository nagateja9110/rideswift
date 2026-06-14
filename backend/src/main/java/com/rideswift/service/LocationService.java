package com.rideswift.service;

import com.rideswift.config.RideswiftProperties;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Driver;
import com.rideswift.repository.DriverRepository;
import com.rideswift.util.GeoUtils;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists driver locations to PostGIS (authoritative) and mirrors them into a
 * Redis GEO set + per-driver key with a short TTL. Redis is best-effort: any
 * failure is logged and swallowed so location updates never fail on a cache miss.
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    static final String GEO_KEY = "drivers:geo";
    static final String LOCATION_KEY_PREFIX = "driver:location:";

    private final DriverRepository driverRepository;
    private final StringRedisTemplate redis;
    private final long cacheTtlSeconds;

    public LocationService(DriverRepository driverRepository,
                           StringRedisTemplate redis,
                           RideswiftProperties properties) {
        this.driverRepository = driverRepository;
        this.redis = redis;
        this.cacheTtlSeconds = properties.location().cacheTtlSeconds();
    }

    @Transactional
    public Driver updateLocation(UUID driverId, double latitude, double longitude) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + driverId));
        driver.setCurrentLocation(GeoUtils.point(latitude, longitude));
        driverRepository.save(driver);
        cacheLocation(driverId, latitude, longitude);
        return driver;
    }

    /** Mirrors the latest fix into Redis. Best-effort. */
    public void cacheLocation(UUID driverId, double latitude, double longitude) {
        String member = driverId.toString();
        try {
            redis.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), member);
            redis.opsForValue().set(
                    LOCATION_KEY_PREFIX + member,
                    latitude + "," + longitude + "," + System.currentTimeMillis(),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (RuntimeException ex) {
            log.debug("Redis location cache write failed for {} (continuing): {}", member, ex.toString());
        }
    }

    /** Removes a driver from the live GEO cache (e.g. when they go offline). */
    public void evict(UUID driverId) {
        String member = driverId.toString();
        try {
            redis.opsForGeo().remove(GEO_KEY, member);
            redis.delete(LOCATION_KEY_PREFIX + member);
        } catch (RuntimeException ex) {
            log.debug("Redis eviction failed for {} (continuing): {}", member, ex.toString());
        }
    }

    /**
     * Candidate driver ids near a point from the Redis GEO set, nearest first.
     * Returns null when Redis is unavailable so callers can fall back to PostGIS.
     */
    public java.util.List<UUID> nearbyCandidateIds(double latitude, double longitude,
                                                   double radiusMeters, int limit) {
        try {
            var args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                    .includeDistance().sortAscending().limit(limit);
            var circle = new Circle(
                    new Point(longitude, latitude),
                    new Distance(radiusMeters / 1000.0, Metrics.KILOMETERS));
            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    redis.opsForGeo().radius(GEO_KEY, circle, args);
            if (results == null) {
                return null;
            }
            return results.getContent().stream()
                    .map(r -> UUID.fromString(r.getContent().getName()))
                    .toList();
        } catch (RuntimeException ex) {
            log.debug("Redis GEO search failed (falling back to PostGIS): {}", ex.toString());
            return null;
        }
    }
}
