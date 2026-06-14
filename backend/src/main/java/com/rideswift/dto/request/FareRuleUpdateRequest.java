package com.rideswift.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FareRuleUpdateRequest(
        @NotNull @DecimalMin("0.0") BigDecimal baseFare,
        @NotNull @DecimalMin("0.0") BigDecimal perKmRate,
        @NotNull @DecimalMin("0.0") BigDecimal perMinuteRate,
        @NotNull @DecimalMin("1.0") BigDecimal surgeMultiplier
) {
}
