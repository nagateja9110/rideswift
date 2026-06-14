package com.rideswift.dto.request;

/** Body for starting a ride — the pickup PIN the passenger reads out to the driver. */
public record StartRideRequest(String pin) {
}
