package com.rideswift.dto.response;

import com.rideswift.model.Vehicle;
import com.rideswift.model.VehicleType;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID driverId,
        String make,
        String model,
        Integer year,
        String licensePlate,
        VehicleType vehicleType
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getDriver().getId(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getLicensePlate(),
                vehicle.getVehicleType());
    }
}
