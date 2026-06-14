package com.rideswift.dto.request;

import com.rideswift.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be a valid phone number")
        String phone,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role
) {
}
