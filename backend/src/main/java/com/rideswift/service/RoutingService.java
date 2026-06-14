package com.rideswift.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideswift.config.RideswiftProperties;
import com.rideswift.config.RoutingProperties;
import com.rideswift.util.GeoUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Real road routing via an OSRM server, fronted by a Redis cache. Every call
 * returns a usable result: when OSRM is disabled or unreachable it falls back to a
 * straight-line (haversine) estimate so fares and the map never break.
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    private static final String CACHE_PREFIX = "route:";

    /**
     * Max distance (metres) OSRM may snap a requested point to a road before we
     * distrust the result. A self-hosted extract only covers one region (here,
     * Coimbatore); a point outside it snaps to the nearest in-region road, which
     * can be hundreds of km away and yields a nonsensical route (often distance 0
     * with both ends collapsed onto the same node). Beyond this threshold we fall
     * back to the straight-line estimate instead of trusting the bogus route.
     */
    private static final double MAX_SNAP_METERS = 5_000.0;

    /** Distance (km), duration (min), polyline as [lat,lng] pairs, and the source engine. */
    public record RouteResult(BigDecimal distanceKm, int durationMinutes,
                              List<double[]> geometry, String source) {
    }

    private final RestClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String profile;
    private final long cacheTtlSeconds;
    private final double averageSpeedKmph;

    public RoutingService(RestClient routingRestClient,
                          StringRedisTemplate redis,
                          ObjectMapper mapper,
                          RoutingProperties routingProps,
                          RideswiftProperties rideProps) {
        this.client = routingRestClient;
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = routingProps.enabled();
        this.profile = routingProps.profile();
        this.cacheTtlSeconds = routingProps.cacheTtlSeconds();
        this.averageSpeedKmph = rideProps.fare().averageSpeedKmph();
    }

    public RouteResult route(double pLat, double pLng, double dLat, double dLng) {
        if (!enabled) {
            return haversineFallback(pLat, pLng, dLat, dLng);
        }
        String cacheKey = String.format(Locale.ROOT, "%s%.5f,%.5f;%.5f,%.5f",
                CACHE_PREFIX, pLat, pLng, dLat, dLng);

        RouteResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            // OSRM coordinate order is lng,lat.
            String coords = String.format(Locale.ROOT, "%f,%f;%f,%f", pLng, pLat, dLng, dLat);
            OsrmResponse resp = client.get()
                    .uri(uri -> uri.path("/route/v1/" + profile + "/" + coords)
                            .queryParam("overview", "full")
                            .queryParam("geometries", "geojson")
                            .build())
                    .retrieve()
                    .body(OsrmResponse.class);

            RouteResult result = toResult(resp, pLat, pLng, dLat, dLng);
            if (result == null) {
                return haversineFallback(pLat, pLng, dLat, dLng);
            }
            writeCache(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.warn("OSRM routing failed ({},{})->({},{}): {}", pLat, pLng, dLat, dLng, e.getMessage());
            return haversineFallback(pLat, pLng, dLat, dLng);
        }
    }

    private RouteResult toResult(OsrmResponse resp,
                                 double pLat, double pLng, double dLat, double dLng) {
        if (resp == null || !"Ok".equals(resp.code())
                || resp.routes() == null || resp.routes().isEmpty()) {
            return null;
        }
        OsrmRoute route = resp.routes().get(0);
        if (route.geometry() == null || route.geometry().coordinates() == null) {
            return null;
        }
        // Reject results where OSRM snapped a requested point onto a road far outside
        // the loaded region — the route would describe a different place entirely.
        if (resp.waypoints() != null) {
            for (OsrmWaypoint wp : resp.waypoints()) {
                if (wp.distance() > MAX_SNAP_METERS) {
                    log.warn("OSRM snapped {}m away (outside loaded region); using straight-line estimate",
                            Math.round(wp.distance()));
                    return null;
                }
            }
        }
        // Distinct endpoints that collapse to a zero-length route are also bogus
        // (typically both ends snapped onto the same node).
        if (route.distance() <= 0 && GeoUtils.haversineKm(pLat, pLng, dLat, dLng) > 0.05) {
            return null;
        }
        // GeoJSON coordinates are [lng,lat]; flip to [lat,lng] for Leaflet.
        List<double[]> geometry = route.geometry().coordinates().stream()
                .map(c -> new double[]{c.get(1), c.get(0)})
                .toList();
        BigDecimal km = BigDecimal.valueOf(route.distance() / 1000.0).setScale(3, RoundingMode.HALF_UP);
        int minutes = Math.max(1, (int) Math.ceil(route.duration() / 60.0));
        return new RouteResult(km, minutes, geometry, "osrm");
    }

    private RouteResult haversineFallback(double pLat, double pLng, double dLat, double dLng) {
        double km = GeoUtils.haversineKm(pLat, pLng, dLat, dLng);
        BigDecimal distanceKm = BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP);
        int minutes = Math.max(1, (int) Math.ceil(km / averageSpeedKmph * 60));
        List<double[]> geometry = List.of(new double[]{pLat, pLng}, new double[]{dLat, dLng});
        return new RouteResult(distanceKm, minutes, geometry, "haversine");
    }

    private RouteResult readCache(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : mapper.readValue(json, RouteResult.class);
        } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    private void writeCache(String key, RouteResult result) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(result),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException e) {
            // best-effort cache
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmResponse(String code, List<OsrmRoute> routes, List<OsrmWaypoint> waypoints) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmRoute(double distance, double duration, OsrmGeometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmGeometry(List<List<Double>> coordinates) {
    }

    /** A requested point snapped onto the road network; {@code distance} is the snap offset in metres. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmWaypoint(double distance) {
    }
}
