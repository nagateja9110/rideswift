package com.rideswift.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 120) String name,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be a valid phone number")
        String phone
) {
}
