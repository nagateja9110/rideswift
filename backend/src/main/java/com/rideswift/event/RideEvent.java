package com.rideswift.event;

import com.rideswift.model.Ride;
import com.rideswift.model.VehicleType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable snapshot of a ride lifecycle change, broadcast to {@link RideEventListener}
 * observers. Carries the ids/values observers need without re-touching the DB.
 */
public record RideEvent(
        RideEventType type,
        UUID rideId,
        UUID passengerUserId,
        UUID driverId,
        UUID driverUserId,
        VehicleType vehicleType,
        BigDecimal fare,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropoffLatitude,
        Double dropoffLongitude,
        String pickupAddress,
        String dropoffAddress,
        BigDecimal distanceKm,
        Integer durationMinutes
) {
    public static RideEvent of(RideEventType type, Ride ride) {
        UUID driverId = ride.getDriver() != null ? ride.getDriver().getId() : null;
        UUID driverUserId = ride.getDriver() != null ? ride.getDriver().getUser().getId() : null;
        return new RideEvent(
                type,
                ride.getId(),
                ride.getPassenger().getId(),
                driverId,
                driverUserId,
                ride.getVehicleType(),
                ride.getActualFare() != null ? ride.getActualFare() : ride.getEstimatedFare(),
                ride.getPickupLocation() != null ? ride.getPickupLocation().getY() : null,
                ride.getPickupLocation() != null ? ride.getPickupLocation().getX() : null,
                ride.getDropoffLocation() != null ? ride.getDropoffLocation().getY() : null,
                ride.getDropoffLocation() != null ? ride.getDropoffLocation().getX() : null,
                ride.getPickupAddress(),
                ride.getDropoffAddress(),
                ride.getDistanceKm(),
                ride.getDurationMinutes());
    }
}
