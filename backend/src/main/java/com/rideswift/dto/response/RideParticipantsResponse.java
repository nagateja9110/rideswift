package com.rideswift.dto.response;

import com.rideswift.model.Driver;
import com.rideswift.model.User;
import com.rideswift.model.Vehicle;
import com.rideswift.model.VehicleType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contact details for the two parties of a ride. The driver block (and the
 * vehicle within it) is null until a driver has been assigned. The frontend
 * shows the caller their counterpart.
 */
public record RideParticipantsResponse(
        PassengerInfo passenger,
        DriverInfo driver
) {
    public record PassengerInfo(UUID userId, String name, String phone) {
        public static PassengerInfo from(User user) {
            return new PassengerInfo(user.getId(), user.getName(), user.getPhone());
        }
    }

    public record VehicleInfo(String make, String model, Integer year, String licensePlate, VehicleType vehicleType) {
        public static VehicleInfo from(Vehicle v) {
            return new VehicleInfo(v.getMake(), v.getModel(), v.getYear(), v.getLicensePlate(), v.getVehicleType());
        }
    }

    public record DriverInfo(
            UUID driverId,
            UUID userId,
            String name,
            String phone,
            BigDecimal rating,
            VehicleInfo vehicle
    ) {
        public static DriverInfo from(Driver driver, Vehicle vehicle) {
            return new DriverInfo(
                    driver.getId(),
                    driver.getUser().getId(),
                    driver.getUser().getName(),
                    driver.getUser().getPhone(),
                    driver.getRating(),
                    vehicle != null ? VehicleInfo.from(vehicle) : null);
        }
    }
}
