package com.rideswift.service;

import com.rideswift.dto.response.SurgeResponse;
import com.rideswift.model.SurgeZone;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.RideRepository;
import com.rideswift.repository.SurgeZoneRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes a demand/supply surge multiplier for a pickup location. The pickup's
 * surge zone is found via PostGIS; the multiplier is
 * {@code clamp(round((demand + predictedDemand) / supply), zone.min, zone.max)}.
 * Results are cached in Redis per zone with a short TTL (best-effort).
 */
@Service
public class SurgePricingService {

    private static final Logger log = LoggerFactory.getLogger(SurgePricingService.class);
    private static final String CACHE_PREFIX = "surge:zone:";

    private final SurgeZoneRepository surgeZoneRepository;
    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;
    private final DemandPredictionService demandPrediction;
    private final StringRedisTemplate redis;
    private final int windowMinutes;
    private final long cacheTtlSeconds;

    public SurgePricingService(SurgeZoneRepository surgeZoneRepository,
                               RideRepository rideRepository,
                               DriverRepository driverRepository,
                               DemandPredictionService demandPrediction,
                               StringRedisTemplate redis,
                               @Value("${rideswift.surge.window-minutes:15}") int windowMinutes,
                               @Value("${rideswift.surge.cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this.surgeZoneRepository = surgeZoneRepository;
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.demandPrediction = demandPrediction;
        this.redis = redis;
        this.windowMinutes = windowMinutes;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    /** Multiplier used by fare calculation. Reads the per-zone cache when warm. */
    @Transactional(readOnly = true)
    public BigDecimal currentMultiplier(double latitude, double longitude) {
        SurgeZone zone = surgeZoneRepository.findContaining(latitude, longitude).orElse(null);
        if (zone == null) {
            return BigDecimal.ONE;
        }
        BigDecimal cached = readCache(zone.getId().toString());
        if (cached != null) {
            return cached;
        }
        return compute(zone).surgeMultiplier();
    }

    /** Full surge breakdown for the surge endpoint; always recomputes (and refreshes cache). */
    @Transactional(readOnly = true)
    public SurgeResponse describe(double latitude, double longitude) {
        SurgeZone zone = surgeZoneRepository.findContaining(latitude, longitude).orElse(null);
        return zone == null ? SurgeResponse.noZone() : compute(zone);
    }

    private SurgeResponse compute(SurgeZone zone) {
        Instant since = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        long demand = rideRepository.countRequestsInZoneSince(zone.getId(), since);
        long supply = driverRepository.countAvailableDriversInZone(zone.getId());

        double predicted = demandPrediction.predictAdditionalDemand(demand, Instant.now());
        double effectiveDemand = demand + predicted;
        double ratio = effectiveDemand / Math.max(1L, supply);

        BigDecimal multiplier = clamp(
                BigDecimal.valueOf(Math.max(1.0, ratio)).setScale(1, RoundingMode.HALF_UP),
                zone.getMinMultiplier(), zone.getMaxMultiplier());

        writeCache(zone.getId().toString(), multiplier);
        return new SurgeResponse(zone.getId(), zone.getName(), multiplier, demand, supply);
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        return value.compareTo(max) > 0 ? max : value;
    }

    private BigDecimal readCache(String zoneId) {
        try {
            String value = redis.opsForValue().get(CACHE_PREFIX + zoneId);
            return value != null ? new BigDecimal(value) : null;
        } catch (RuntimeException ex) {
            log.debug("Surge cache read failed (continuing): {}", ex.toString());
            return null;
        }
    }

    private void writeCache(String zoneId, BigDecimal multiplier) {
        try {
            redis.opsForValue().set(CACHE_PREFIX + zoneId, multiplier.toPlainString(),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (RuntimeException ex) {
            log.debug("Surge cache write failed (continuing): {}", ex.toString());
        }
    }
}
