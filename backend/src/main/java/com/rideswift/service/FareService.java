package com.rideswift.service;

import com.rideswift.dto.request.FareEstimateRequest;
import com.rideswift.dto.request.FareRuleUpdateRequest;
import com.rideswift.dto.response.FareEstimateResponse;
import com.rideswift.dto.response.FareRuleResponse;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.FareRule;
import com.rideswift.model.VehicleType;
import com.rideswift.repository.FareRuleRepository;
import com.rideswift.strategy.FareStrategy;
import com.rideswift.strategy.StandardFareStrategy;
import com.rideswift.strategy.SurgeFareStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FareService {

    public record FareQuote(BigDecimal distanceKm, int durationMinutes, BigDecimal fare,
                            String strategy, BigDecimal surgeMultiplier) {
    }

    private static final String CURRENCY = "INR";

    private final FareRuleRepository fareRuleRepository;
    private final Map<String, FareStrategy> strategies;
    private final SurgePricingService surgePricingService;
    private final RoutingService routingService;

    public FareService(FareRuleRepository fareRuleRepository,
                       List<FareStrategy> strategyBeans,
                       SurgePricingService surgePricingService,
                       RoutingService routingService) {
        this.fareRuleRepository = fareRuleRepository;
        this.strategies = strategyBeans.stream()
                .collect(Collectors.toMap(FareStrategy::name, Function.identity()));
        this.surgePricingService = surgePricingService;
        this.routingService = routingService;
    }

    /** Computes distance, duration and fare for a trip, applying surge at the pickup. */
    @Transactional(readOnly = true)
    public FareQuote quote(VehicleType vehicleType,
                           double pickupLat, double pickupLng,
                           double dropoffLat, double dropoffLng) {
        // Real road distance/duration from OSRM (falls back to straight-line internally).
        RoutingService.RouteResult route =
                routingService.route(pickupLat, pickupLng, dropoffLat, dropoffLng);
        BigDecimal distanceKm = route.distanceKm();
        int durationMinutes = route.durationMinutes();

        FareRule rule = activeRule(vehicleType);
        FareStrategy base = strategies.get(StandardFareStrategy.NAME);

        // Decorate the base strategy with the dynamic pickup-zone surge multiplier.
        BigDecimal surge = surgePricingService.currentMultiplier(pickupLat, pickupLng);
        FareStrategy strategy = surge.compareTo(BigDecimal.ONE) > 0
                ? new SurgeFareStrategy(base, surge)
                : base;

        BigDecimal fare = strategy.calculate(rule, distanceKm, BigDecimal.valueOf(durationMinutes));
        return new FareQuote(distanceKm, durationMinutes, fare, strategy.name(), surge);
    }

    @Transactional(readOnly = true)
    public FareEstimateResponse estimate(FareEstimateRequest request) {
        FareQuote quote = quote(request.vehicleType(),
                request.pickupLatitude(), request.pickupLongitude(),
                request.dropoffLatitude(), request.dropoffLongitude());
        return new FareEstimateResponse(
                request.vehicleType(),
                quote.distanceKm(),
                quote.durationMinutes(),
                quote.fare(),
                CURRENCY,
                quote.strategy(),
                quote.surgeMultiplier());
    }

    @Transactional(readOnly = true)
    public List<FareRuleResponse> activeRules() {
        Instant now = Instant.now();
        return java.util.Arrays.stream(VehicleType.values())
                .map(type -> activeRuleOrNull(type, now))
                .filter(java.util.Objects::nonNull)
                .map(FareRuleResponse::from)
                .toList();
    }

    @Transactional
    public FareRuleResponse updateRule(UUID id, FareRuleUpdateRequest request) {
        FareRule rule = fareRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fare rule not found: " + id));
        rule.setBaseFare(request.baseFare());
        rule.setPerKmRate(request.perKmRate());
        rule.setPerMinuteRate(request.perMinuteRate());
        rule.setSurgeMultiplier(request.surgeMultiplier());
        return FareRuleResponse.from(fareRuleRepository.save(rule));
    }

    private FareRule activeRule(VehicleType vehicleType) {
        FareRule rule = activeRuleOrNull(vehicleType, Instant.now());
        if (rule == null) {
            throw new ResourceNotFoundException("No active fare rule for vehicle type " + vehicleType);
        }
        return rule;
    }

    private FareRule activeRuleOrNull(VehicleType vehicleType, Instant at) {
        List<FareRule> rules = fareRuleRepository.findActive(vehicleType, at, Limit.of(1));
        return rules.isEmpty() ? null : rules.get(0);
    }
}
