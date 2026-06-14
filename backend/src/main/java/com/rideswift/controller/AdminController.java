package com.rideswift.controller;

import com.rideswift.dto.request.BroadcastRequest;
import com.rideswift.dto.request.FareAdjustmentRequest;
import com.rideswift.dto.request.SurgeZoneRequest;
import com.rideswift.dto.response.DashboardResponse;
import com.rideswift.dto.response.DriverResponse;
import com.rideswift.dto.response.RideAuditEntry;
import com.rideswift.dto.response.RideResponse;
import com.rideswift.dto.response.SurgeZoneResponse;
import com.rideswift.model.VerificationStatus;
import com.rideswift.payment.PaymentOutageSimulator;
import com.rideswift.service.AdminService;
import com.rideswift.service.AuditService;
import com.rideswift.service.DriverService;
import com.rideswift.service.SurgeZoneService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;
    private final DriverService driverService;
    private final SurgeZoneService surgeZoneService;
    private final PaymentOutageSimulator outageSimulator;

    public AdminController(AdminService adminService,
                           AuditService auditService,
                           DriverService driverService,
                           SurgeZoneService surgeZoneService,
                           PaymentOutageSimulator outageSimulator) {
        this.adminService = adminService;
        this.auditService = auditService;
        this.driverService = driverService;
        this.surgeZoneService = surgeZoneService;
        this.outageSimulator = outageSimulator;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/drivers/pending")
    public ResponseEntity<List<DriverResponse>> pendingDrivers() {
        return ResponseEntity.ok(driverService.listByVerificationStatus(VerificationStatus.PENDING));
    }

    @PatchMapping("/rides/{rideId}/fare")
    public ResponseEntity<RideResponse> adjustFare(@PathVariable UUID rideId,
                                                   @Valid @RequestBody FareAdjustmentRequest request) {
        return ResponseEntity.ok(adminService.adjustFare(rideId, request));
    }

    @PostMapping("/notifications/broadcast")
    public ResponseEntity<Map<String, Integer>> broadcast(@Valid @RequestBody BroadcastRequest request) {
        return ResponseEntity.ok(Map.of("recipients", adminService.broadcast(request)));
    }

    @GetMapping("/audit/rides/{rideId}")
    public ResponseEntity<List<RideAuditEntry>> rideAudit(@PathVariable UUID rideId) {
        return ResponseEntity.ok(auditService.rideHistory(rideId));
    }

    /** Resilience test affordance: toggles a simulated payment-provider outage. */
    @PostMapping("/payments/outage")
    public ResponseEntity<Map<String, Boolean>> setPaymentOutage(@RequestParam boolean enabled) {
        outageSimulator.setDown(enabled);
        return ResponseEntity.ok(Map.of("paymentOutage", enabled));
    }

    // --- Surge zones (Phase 6) ---

    @PostMapping("/surge-zones")
    public ResponseEntity<SurgeZoneResponse> createSurgeZone(@Valid @RequestBody SurgeZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(surgeZoneService.create(request));
    }

    @GetMapping("/surge-zones")
    public ResponseEntity<List<SurgeZoneResponse>> listSurgeZones() {
        return ResponseEntity.ok(surgeZoneService.list());
    }

    @PatchMapping("/surge-zones/{id}/active")
    public ResponseEntity<SurgeZoneResponse> setSurgeZoneActive(@PathVariable UUID id,
                                                                @RequestParam boolean active) {
        return ResponseEntity.ok(surgeZoneService.setActive(id, active));
    }

    @DeleteMapping("/surge-zones/{id}")
    public ResponseEntity<Void> deleteSurgeZone(@PathVariable UUID id) {
        surgeZoneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
