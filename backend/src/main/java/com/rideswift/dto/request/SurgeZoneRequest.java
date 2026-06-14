package com.rideswift.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * @param coordinates a polygon ring as [longitude, latitude] pairs (auto-closed)
 */
public record SurgeZoneRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull double[][] coordinates,
        @DecimalMin("1.0") BigDecimal minMultiplier,
        @DecimalMin("1.0") BigDecimal maxMultiplier
) {
}
