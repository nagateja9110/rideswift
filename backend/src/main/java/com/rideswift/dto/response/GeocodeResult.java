package com.rideswift.dto.response;

/**
 * A single geocoding hit: a human-readable label and its coordinates.
 * {@code name} is the short primary label (e.g. "Times Square"), {@code displayName}
 * the full address line.
 */
public record GeocodeResult(
        String name,
        String displayName,
        double lat,
        double lng
) {
}
