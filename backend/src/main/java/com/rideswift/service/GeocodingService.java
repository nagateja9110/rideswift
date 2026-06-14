package com.rideswift.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideswift.config.GeocodingProperties;
import com.rideswift.dto.response.GeocodeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Forward + reverse geocoding via Nominatim (OpenStreetMap), fronted by a Redis
 * result cache. Network and parse failures degrade to an empty result rather than
 * propagating — the caller can always fall back to raw map coordinates.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final String SEARCH_CACHE_PREFIX = "geocode:search:";
    private static final String REVERSE_CACHE_PREFIX = "geocode:reverse:";

    private final RestClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final int defaultLimit;
    private final long cacheTtlSeconds;

    public GeocodingService(RestClient geocodingRestClient,
                            StringRedisTemplate redis,
                            ObjectMapper mapper,
                            GeocodingProperties props) {
        this.client = geocodingRestClient;
        this.redis = redis;
        this.mapper = mapper;
        this.defaultLimit = props.limit();
        this.cacheTtlSeconds = props.cacheTtlSeconds();
    }

    /** Forward geocode a free-text query into ranked candidates for autocomplete. */
    public List<GeocodeResult> search(String query, Integer limit) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 3) {
            return List.of();
        }
        int max = limit != null && limit > 0 ? Math.min(limit, 10) : defaultLimit;
        String cacheKey = SEARCH_CACHE_PREFIX + max + ":" + q.toLowerCase(Locale.ROOT);

        List<GeocodeResult> cached = readCachedList(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            NominatimPlace[] places = client.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", q)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 0)
                            .queryParam("limit", max)
                            .build())
                    .retrieve()
                    .body(NominatimPlace[].class);

            List<GeocodeResult> results = toResults(places);
            writeCache(cacheKey, results);
            return results;
        } catch (Exception e) {
            log.warn("Geocoding search failed for '{}': {}", q, e.getMessage());
            return List.of();
        }
    }

    /** Reverse geocode coordinates (e.g. a tapped map point) into a single address. */
    public GeocodeResult reverse(double lat, double lng) {
        String cacheKey = REVERSE_CACHE_PREFIX
                + String.format(Locale.ROOT, "%.5f,%.5f", lat, lng);

        List<GeocodeResult> cached = readCachedList(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? null : cached.get(0);
        }

        try {
            NominatimPlace place = client.get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lng)
                            .queryParam("format", "jsonv2")
                            .build())
                    .retrieve()
                    .body(NominatimPlace.class);

            GeocodeResult result = place != null ? place.toResult() : null;
            writeCache(cacheKey, result != null ? List.of(result) : List.of());
            return result;
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for {},{}: {}", lat, lng, e.getMessage());
            return null;
        }
    }

    private List<GeocodeResult> toResults(NominatimPlace[] places) {
        if (places == null) {
            return List.of();
        }
        List<GeocodeResult> results = new ArrayList<>(places.length);
        for (NominatimPlace p : places) {
            GeocodeResult r = p.toResult();
            if (r != null) {
                results.add(r);
            }
        }
        return results;
    }

    private List<GeocodeResult> readCachedList(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            GeocodeResult[] arr = mapper.readValue(json, GeocodeResult[].class);
            return List.of(arr);
        } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return null; // cache miss / unavailable — fall through to upstream
        }
    }

    private void writeCache(String key, List<GeocodeResult> results) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(results),
                    java.time.Duration.ofSeconds(cacheTtlSeconds));
        } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException e) {
            // Best-effort cache; ignore failures.
        }
    }

    /** Raw Nominatim row; lat/lon arrive as strings. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimPlace(
            String name,
            @JsonProperty("display_name") String displayName,
            String lat,
            String lon
    ) {
        GeocodeResult toResult() {
            if (lat == null || lon == null || displayName == null) {
                return null;
            }
            try {
                String shortName = (name != null && !name.isBlank())
                        ? name
                        : displayName.split(",")[0].trim();
                return new GeocodeResult(shortName, displayName,
                        Double.parseDouble(lat), Double.parseDouble(lon));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
