package com.rideswift.service;

import com.rideswift.dto.request.VehicleRequest;
import com.rideswift.dto.response.VehicleResponse;
import com.rideswift.exception.ConflictException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Driver;
import com.rideswift.model.Vehicle;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.VehicleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;

    public VehicleService(VehicleRepository vehicleRepository, DriverRepository driverRepository) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
    }

    @Transactional
    public VehicleResponse create(UUID driverId, VehicleRequest request) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + driverId));

        if (vehicleRepository.existsByLicensePlateIgnoreCase(request.licensePlate())) {
            throw new ConflictException("License plate already registered");
        }

        Vehicle vehicle = Vehicle.builder()
                .driver(driver)
                .make(request.make())
                .model(request.model())
                .year(request.year())
                .licensePlate(request.licensePlate())
                .vehicleType(request.vehicleType())
                .build();
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public VehicleResponse getById(UUID id) {
        return VehicleResponse.from(loadVehicle(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> getByDriver(UUID driverId) {
        return vehicleRepository.findByDriverId(driverId).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = loadVehicle(id);
        if (!vehicle.getLicensePlate().equalsIgnoreCase(request.licensePlate())
                && vehicleRepository.existsByLicensePlateIgnoreCase(request.licensePlate())) {
            throw new ConflictException("License plate already registered");
        }
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setYear(request.year());
        vehicle.setLicensePlate(request.licensePlate());
        vehicle.setVehicleType(request.vehicleType());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void delete(UUID id) {
        if (!vehicleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Vehicle not found: " + id);
        }
        vehicleRepository.deleteById(id);
    }

    private Vehicle loadVehicle(UUID id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
    }
}
