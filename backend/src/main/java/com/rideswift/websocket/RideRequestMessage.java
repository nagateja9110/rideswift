package com.rideswift.websocket;

import com.rideswift.model.VehicleType;
import java.math.BigDecimal;
import java.util.UUID;

public record RideRequestMessage(
        UUID rideId,
        UUID driverId,
        VehicleType vehicleType,
        double pickupLatitude,
        double pickupLongitude,
        Double dropoffLatitude,
        Double dropoffLongitude,
        String pickupAddress,
        String dropoffAddress,
        BigDecimal estimatedFare,
        BigDecimal distanceKm,
        Integer durationMinutes
) {
}
