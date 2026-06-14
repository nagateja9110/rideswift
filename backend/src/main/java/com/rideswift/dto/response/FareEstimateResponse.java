package com.rideswift.dto.response;

import com.rideswift.model.VehicleType;
import java.math.BigDecimal;

public record FareEstimateResponse(
        VehicleType vehicleType,
        BigDecimal distanceKm,
        Integer durationMinutes,
        BigDecimal estimatedFare,
        String currency,
        String strategy,
        BigDecimal surgeMultiplier
) {
}
