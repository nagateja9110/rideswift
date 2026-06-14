package com.rideswift.dto.request;

import com.rideswift.model.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(max = 60) String make,
        @NotBlank @Size(max = 60) String model,
        @NotNull @Min(1950) @Max(2100) Integer year,
        @NotBlank @Size(max = 20) String licensePlate,
        @NotNull VehicleType vehicleType
) {
}
