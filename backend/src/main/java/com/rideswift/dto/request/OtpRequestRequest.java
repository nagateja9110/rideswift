package com.rideswift.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Step 1 of phone login: ask for an OTP to be sent to this number. */
public record OtpRequestRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be a valid phone number")
        String phone
) {
}
