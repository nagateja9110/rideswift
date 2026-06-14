package com.rideswift.websocket;

import java.util.UUID;

public record DriverLocationMessage(UUID rideId, UUID driverId, double latitude, double longitude, long timestamp) {
}
