package com.rideswift.dto.response;

import com.rideswift.model.FareRule;
import com.rideswift.model.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FareRuleResponse(
        UUID id,
        VehicleType vehicleType,
        BigDecimal baseFare,
        BigDecimal perKmRate,
        BigDecimal perMinuteRate,
        BigDecimal surgeMultiplier,
        Instant effectiveFrom,
        Instant effectiveTo
) {
    public static FareRuleResponse from(FareRule rule) {
        return new FareRuleResponse(
                rule.getId(),
                rule.getVehicleType(),
                rule.getBaseFare(),
                rule.getPerKmRate(),
                rule.getPerMinuteRate(),
                rule.getSurgeMultiplier(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo());
    }
}
