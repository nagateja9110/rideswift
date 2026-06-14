package com.rideswift.dto.response;

import com.rideswift.model.Driver;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

public record NearbyDriverResponse(
        UUID driverId,
        Double latitude,
        Double longitude,
        Double distanceKm
) {
    public static NearbyDriverResponse from(Driver driver, double distanceKm) {
        Point loc = driver.getCurrentLocation();
        return new NearbyDriverResponse(
                driver.getId(),
                loc != null ? loc.getY() : null,
                loc != null ? loc.getX() : null,
                distanceKm);
    }
}
