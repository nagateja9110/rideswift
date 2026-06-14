package com.rideswift.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FareAdjustmentRequest(
        @NotNull @DecimalMin("0.0") BigDecimal actualFare
) {
}
