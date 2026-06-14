package com.rideswift.repository;

import com.rideswift.model.Vehicle;
import com.rideswift.model.VehicleType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByDriverId(UUID driverId);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByDriverIdAndVehicleType(UUID driverId, VehicleType vehicleType);
}
