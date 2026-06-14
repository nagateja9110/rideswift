package com.rideswift.dto.response;

import com.rideswift.model.Ride;
import com.rideswift.model.RideStatus;
import com.rideswift.model.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

public record RideResponse(
        UUID id,
        UUID passengerId,
        UUID driverId,
        VehicleType vehicleType,
        RideStatus status,
        Double pickupLatitude,
        Double pickupLongitude,
        Double dropoffLatitude,
        Double dropoffLongitude,
        String pickupAddress,
        String dropoffAddress,
        BigDecimal estimatedFare,
        BigDecimal actualFare,
        BigDecimal distanceKm,
        Integer durationMinutes,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String pickupPin,
        Instant scheduledAt
) {
    /** Default view — includes the pickup PIN. Use {@link #from(Ride, boolean)} to hide it. */
    public static RideResponse from(Ride ride) {
        return from(ride, true);
    }

    /**
     * @param includePin pass {@code false} for the driver's view — only the passenger
     *                   should see the PIN they read out to the driver at pickup.
     */
    public static RideResponse from(Ride ride, boolean includePin) {
        Point pickup = ride.getPickupLocation();
        Point dropoff = ride.getDropoffLocation();
        return new RideResponse(
                ride.getId(),
                ride.getPassenger().getId(),
                ride.getDriver() != null ? ride.getDriver().getId() : null,
                ride.getVehicleType(),
                ride.getStatus(),
                pickup != null ? pickup.getY() : null,
                pickup != null ? pickup.getX() : null,
                dropoff != null ? dropoff.getY() : null,
                dropoff != null ? dropoff.getX() : null,
                ride.getPickupAddress(),
                ride.getDropoffAddress(),
                ride.getEstimatedFare(),
                ride.getActualFare(),
                ride.getDistanceKm(),
                ride.getDurationMinutes(),
                ride.getRequestedAt(),
                ride.getStartedAt(),
                ride.getCompletedAt(),
                includePin ? ride.getPickupPin() : null,
                ride.getScheduledAt());
    }
}
