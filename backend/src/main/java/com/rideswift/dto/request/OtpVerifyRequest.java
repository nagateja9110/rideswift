package com.rideswift.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Step 2 of phone login: verify the code and sign in. */
public record OtpVerifyRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be a valid phone number")
        String phone,
        @NotBlank String code,
        String name   // optional display name for a first-time (new) account
) {
}
