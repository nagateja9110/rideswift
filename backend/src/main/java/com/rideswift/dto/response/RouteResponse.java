package com.rideswift.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * A routed trip: real road distance/duration plus the polyline geometry as an
 * ordered list of {@code [latitude, longitude]} pairs for the map. {@code source}
 * is "osrm" for a real route or "haversine" when OSRM was unavailable (geometry
 * is then just the straight pickup→dropoff segment).
 */
public record RouteResponse(
        BigDecimal distanceKm,
        int durationMinutes,
        List<double[]> geometry,
        String source
) {
}
