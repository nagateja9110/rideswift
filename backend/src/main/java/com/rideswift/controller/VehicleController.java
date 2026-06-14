package com.rideswift.controller;

import com.rideswift.dto.request.VehicleRequest;
import com.rideswift.dto.response.VehicleResponse;
import com.rideswift.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping("/drivers/{driverId}/vehicles")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<VehicleResponse> create(@PathVariable UUID driverId,
                                                  @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(driverId, request));
    }

    @GetMapping("/drivers/{driverId}/vehicles")
    public ResponseEntity<List<VehicleResponse>> getByDriver(@PathVariable UUID driverId) {
        return ResponseEntity.ok(vehicleService.getByDriver(driverId));
    }

    @GetMapping("/vehicles/{id}")
    public ResponseEntity<VehicleResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(vehicleService.getById(id));
    }

    @PutMapping("/vehicles/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<VehicleResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(vehicleService.update(id, request));
    }

    @DeleteMapping("/vehicles/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
