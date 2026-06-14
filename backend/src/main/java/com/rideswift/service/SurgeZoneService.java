package com.rideswift.service;

import com.rideswift.dto.request.SurgeZoneRequest;
import com.rideswift.dto.response.SurgeZoneResponse;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.SurgeZone;
import com.rideswift.repository.SurgeZoneRepository;
import com.rideswift.util.GeoUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurgeZoneService {

    private static final BigDecimal DEFAULT_MAX = new BigDecimal("3.00");

    private final SurgeZoneRepository surgeZoneRepository;

    public SurgeZoneService(SurgeZoneRepository surgeZoneRepository) {
        this.surgeZoneRepository = surgeZoneRepository;
    }

    @Transactional
    public SurgeZoneResponse create(SurgeZoneRequest request) {
        BigDecimal min = request.minMultiplier() != null ? request.minMultiplier() : BigDecimal.ONE;
        BigDecimal max = request.maxMultiplier() != null ? request.maxMultiplier() : DEFAULT_MAX;
        if (max.compareTo(min) < 0) {
            throw new IllegalArgumentException("maxMultiplier must be >= minMultiplier");
        }
        SurgeZone zone = SurgeZone.builder()
                .name(request.name())
                .area(GeoUtils.polygon(request.coordinates()))
                .minMultiplier(min)
                .maxMultiplier(max)
                .active(true)
                .build();
        return SurgeZoneResponse.from(surgeZoneRepository.save(zone));
    }

    @Transactional(readOnly = true)
    public List<SurgeZoneResponse> list() {
        return surgeZoneRepository.findAll().stream().map(SurgeZoneResponse::from).toList();
    }

    @Transactional
    public SurgeZoneResponse setActive(UUID id, boolean active) {
        SurgeZone zone = surgeZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Surge zone not found: " + id));
        zone.setActive(active);
        return SurgeZoneResponse.from(surgeZoneRepository.save(zone));
    }

    @Transactional
    public void delete(UUID id) {
        if (!surgeZoneRepository.existsById(id)) {
            throw new ResourceNotFoundException("Surge zone not found: " + id);
        }
        surgeZoneRepository.deleteById(id);
    }
}
