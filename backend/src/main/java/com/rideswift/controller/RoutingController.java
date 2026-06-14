package com.rideswift.controller;

import com.rideswift.dto.response.RouteResponse;
import com.rideswift.service.RoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/route")
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    /** Road route + distance/duration between two points, for the map polyline and ETA. */
    @GetMapping
    public ResponseEntity<RouteResponse> route(
            @RequestParam("pickupLat") double pickupLat,
            @RequestParam("pickupLng") double pickupLng,
            @RequestParam("dropoffLat") double dropoffLat,
            @RequestParam("dropoffLng") double dropoffLng) {
        RoutingService.RouteResult r =
                routingService.route(pickupLat, pickupLng, dropoffLat, dropoffLng);
        return ResponseEntity.ok(
                new RouteResponse(r.distanceKm(), r.durationMinutes(), r.geometry(), r.source()));
    }
}
