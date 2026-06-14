package com.rideswift.websocket;

import com.rideswift.model.RideStatus;
import java.util.UUID;

public record RideStatusMessage(UUID rideId, RideStatus status, UUID driverId, String message) {
}
