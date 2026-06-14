package com.rideswift.service;

import com.rideswift.config.RideswiftProperties;
import com.rideswift.model.Driver;
import com.rideswift.model.VehicleType;
import com.rideswift.model.VerificationStatus;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.VehicleRepository;
import com.rideswift.util.GeoUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the nearest eligible drivers for a pickup. Tries the Redis GEO cache
 * first (fast path) and falls back to the authoritative PostGIS query when the
 * cache is empty or unavailable, as described in the design's matching flow.
 */
@Service
public class MatchingService {

    public record Candidate(Driver driver, double distanceKm) {
    }

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final LocationService locationService;
    private final double radiusMeters;
    private final int candidateLimit;

    public MatchingService(DriverRepository driverRepository,
                           VehicleRepository vehicleRepository,
                           LocationService locationService,
                           RideswiftProperties properties) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.locationService = locationService;
        this.radiusMeters = properties.matching().searchRadiusMeters();
        this.candidateLimit = properties.matching().candidateLimit();
    }

    @Transactional(readOnly = true)
    public List<Candidate> findCandidates(double lat, double lng, VehicleType vehicleType) {
        List<Candidate> viaCache = fromRedis(lat, lng, vehicleType);
        if (!viaCache.isEmpty()) {
            return viaCache;
        }
        return fromPostgis(lat, lng, vehicleType);
    }

    private List<Candidate> fromRedis(double lat, double lng, VehicleType vehicleType) {
        List<UUID> ids = locationService.nearbyCandidateIds(lat, lng, radiusMeters, candidateLimit * 3);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Driver> byId = driverRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Driver::getId, Function.identity()));

        List<Candidate> candidates = new ArrayList<>();
        for (UUID id : ids) {              // preserve Redis distance ordering
            Driver driver = byId.get(id);
            if (driver != null && isEligible(driver, vehicleType)) {
                candidates.add(new Candidate(driver, distanceKm(driver, lat, lng)));
            }
            if (candidates.size() >= candidateLimit) {
                break;
            }
        }
        return candidates;
    }

    private List<Candidate> fromPostgis(double lat, double lng, VehicleType vehicleType) {
        return driverRepository
                .findNearbyAvailable(lat, lng, radiusMeters, vehicleType.name(), candidateLimit)
                .stream()
                .map(d -> new Candidate(d, distanceKm(d, lat, lng)))
                .sorted(Comparator.comparingDouble(Candidate::distanceKm))
                .toList();
    }

    private boolean isEligible(Driver driver, VehicleType vehicleType) {
        return driver.isAvailable()
                && driver.getVerificationStatus() == VerificationStatus.VERIFIED
                && driver.getCurrentLocation() != null
                && vehicleRepository.existsByDriverIdAndVehicleType(driver.getId(), vehicleType);
    }

    private double distanceKm(Driver driver, double lat, double lng) {
        Point loc = driver.getCurrentLocation();
        return GeoUtils.haversineKm(lat, lng, loc.getY(), loc.getX());
    }
}
