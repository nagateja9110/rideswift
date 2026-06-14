package com.rideswift.controller;

import com.rideswift.dto.request.AvailabilityRequest;
import com.rideswift.dto.request.DriverRegistrationRequest;
import com.rideswift.dto.request.LocationUpdateRequest;
import com.rideswift.dto.response.DriverResponse;
import com.rideswift.dto.response.NearbyDriverResponse;
import com.rideswift.model.VehicleType;
import com.rideswift.model.VerificationStatus;
import com.rideswift.security.SecurityUtils;
import com.rideswift.service.DriverService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    /** Registers the currently authenticated user as a driver. */
    @PostMapping("/register")
    public ResponseEntity<DriverResponse> register(@Valid @RequestBody DriverRegistrationRequest request) {
        DriverResponse response = driverService.register(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DriverResponse>> getAll() {
        return ResponseEntity.ok(driverService.getAll());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> myProfile() {
        return ResponseEntity.ok(driverService.getByUserId(SecurityUtils.currentUserId()));
    }

    /** Driver pushes their current GPS location (PostGIS + Redis cache). */
    @PatchMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> updateLocation(@Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.ok(driverService.updateLocation(
                SecurityUtils.currentUserId(), request.latitude(), request.longitude()));
    }

    /** Driver toggles online/offline. */
    @PatchMapping("/availability")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverResponse> setAvailability(@Valid @RequestBody AvailabilityRequest request) {
        return ResponseEntity.ok(driverService.setAvailability(
                SecurityUtils.currentUserId(), request.available()));
    }

    /** Nearest available drivers for a pickup point and vehicle type. */
    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyDriverResponse>> nearby(@RequestParam double lat,
                                                             @RequestParam double lng,
                                                             @RequestParam VehicleType vehicleType) {
        return ResponseEntity.ok(driverService.findNearby(lat, lng, vehicleType));
    }

    /** All available drivers within a radius (default 5 km) for the passenger map's ambient car layer. */
    @GetMapping("/nearby/visible")
    public ResponseEntity<List<NearbyDriverResponse>> visibleNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radiusKm) {
        return ResponseEntity.ok(driverService.findVisibleNearby(lat, lng, radiusKm));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.getById(id));
    }

    /** Admin verifies a driver, allowing them to go online. */
    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DriverResponse> verify(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.setVerificationStatus(id, VerificationStatus.VERIFIED));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DriverResponse> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(driverService.setVerificationStatus(id, VerificationStatus.REJECTED));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        driverService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
