package com.rideswift.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record SurgeResponse(
        UUID zoneId,
        String zoneName,
        BigDecimal surgeMultiplier,
        long demand,
        long supply
) {
    public static SurgeResponse noZone() {
        return new SurgeResponse(null, null, BigDecimal.ONE, 0, 0);
    }
}
