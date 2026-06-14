package com.rideswift.dto.request;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(
        @NotNull Boolean available
) {
}
