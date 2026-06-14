package com.rideswift.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Exchanges a verified Firebase ID token for a RideSwift session. */
public record FirebaseAuthRequest(
        @NotBlank String idToken,
        String name   // optional display name for a first-time account
) {
}
