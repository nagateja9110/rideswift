package com.rideswift.dto.response;

import com.rideswift.model.SurgeZone;
import java.math.BigDecimal;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;

public record SurgeZoneResponse(
        UUID id,
        String name,
        BigDecimal minMultiplier,
        BigDecimal maxMultiplier,
        boolean active,
        double[][] coordinates
) {
    public static SurgeZoneResponse from(SurgeZone zone) {
        Coordinate[] ring = zone.getArea().getExteriorRing().getCoordinates();
        double[][] coords = new double[ring.length][2];
        for (int i = 0; i < ring.length; i++) {
            coords[i][0] = ring[i].getX();   // longitude
            coords[i][1] = ring[i].getY();   // latitude
        }
        return new SurgeZoneResponse(
                zone.getId(), zone.getName(),
                zone.getMinMultiplier(), zone.getMaxMultiplier(),
                zone.isActive(), coords);
    }
}
