package com.rideswift.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverRegistrationRequest(
        @NotBlank @Size(max = 64) String licenseNumber
) {
}
