package com.rideswift.controller;

import com.rideswift.dto.response.GeocodeResult;
import com.rideswift.service.GeocodingService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geocode")
public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    /** Autocomplete: free-text address/place search. */
    @GetMapping("/search")
    public ResponseEntity<List<GeocodeResult>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(geocodingService.search(query, limit));
    }

    /** Reverse geocode a coordinate (e.g. a tapped map point) into an address. */
    @GetMapping("/reverse")
    public ResponseEntity<GeocodeResult> reverse(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng) {
        GeocodeResult result = geocodingService.reverse(lat, lng);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }
}
