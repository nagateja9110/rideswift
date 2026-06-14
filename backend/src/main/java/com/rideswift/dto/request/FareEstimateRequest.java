package com.rideswift.dto.request;

import com.rideswift.model.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record FareEstimateRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double pickupLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double pickupLongitude,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double dropoffLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double dropoffLongitude,
        @NotNull VehicleType vehicleType
) {
}
