package com.rideswift.dto.request;

import com.rideswift.model.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RideRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double pickupLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double pickupLongitude,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double dropoffLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double dropoffLongitude,
        @Size(max = 500) String pickupAddress,
        @Size(max = 500) String dropoffAddress,
        @NotNull VehicleType vehicleType,
        Instant scheduledAt   // null = ride now; future instant = book for later
) {
}
