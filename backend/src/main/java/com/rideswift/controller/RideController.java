package com.rideswift.controller;

import com.rideswift.dto.request.RideRequest;
import com.rideswift.dto.request.StartRideRequest;
import com.rideswift.dto.response.RideResponse;
import com.rideswift.security.SecurityUtils;
import com.rideswift.service.RideService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping("/request")
    public ResponseEntity<RideResponse> request(@Valid @RequestBody RideRequest request) {
        RideResponse response = rideService.request(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<RideResponse>> history() {
        return ResponseEntity.ok(rideService.history(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> get(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.get(SecurityUtils.currentUserId(), rideId));
    }

    @PatchMapping("/{rideId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> accept(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.accept(SecurityUtils.currentUserId(), rideId));
    }

    @PatchMapping("/{rideId}/decline")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> decline(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.decline(SecurityUtils.currentUserId(), rideId));
    }

    @PatchMapping("/{rideId}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> start(@PathVariable UUID rideId,
            @RequestBody(required = false) StartRideRequest request) {
        String pin = request != null ? request.pin() : null;
        return ResponseEntity.ok(rideService.start(SecurityUtils.currentUserId(), rideId, pin));
    }

    @PatchMapping("/{rideId}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<RideResponse> complete(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.complete(SecurityUtils.currentUserId(), rideId));
    }

    @PatchMapping("/{rideId}/cancel")
    public ResponseEntity<RideResponse> cancel(@PathVariable UUID rideId) {
        return ResponseEntity.ok(rideService.cancel(SecurityUtils.currentUserId(), rideId));
    }
}
