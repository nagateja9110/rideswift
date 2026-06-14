package com.rideswift.controller;

import com.rideswift.dto.request.FareEstimateRequest;
import com.rideswift.dto.request.FareRuleUpdateRequest;
import com.rideswift.dto.response.FareEstimateResponse;
import com.rideswift.dto.response.FareRuleResponse;
import com.rideswift.dto.response.SurgeResponse;
import com.rideswift.service.FareService;
import com.rideswift.service.SurgePricingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fare")
public class FareController {

    private final FareService fareService;
    private final SurgePricingService surgePricingService;

    public FareController(FareService fareService, SurgePricingService surgePricingService) {
        this.fareService = fareService;
        this.surgePricingService = surgePricingService;
    }

    @GetMapping("/surge")
    public ResponseEntity<SurgeResponse> surge(@RequestParam double lat, @RequestParam double lng) {
        return ResponseEntity.ok(surgePricingService.describe(lat, lng));
    }

    @PostMapping("/estimate")
    public ResponseEntity<FareEstimateResponse> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(fareService.estimate(request));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<FareRuleResponse>> rules() {
        return ResponseEntity.ok(fareService.activeRules());
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FareRuleResponse> updateRule(@PathVariable UUID id,
                                                       @Valid @RequestBody FareRuleUpdateRequest request) {
        return ResponseEntity.ok(fareService.updateRule(id, request));
    }
}
