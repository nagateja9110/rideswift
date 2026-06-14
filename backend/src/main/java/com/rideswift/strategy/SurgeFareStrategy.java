package com.rideswift.strategy;

import com.rideswift.model.FareRule;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decorator (Decorator pattern) that applies a dynamic surge multiplier on top
 * of a delegate fare strategy. Constructed per request with the multiplier from
 * {@code SurgePricingService}, so it is intentionally not a singleton bean.
 */
public class SurgeFareStrategy implements FareStrategy {

    public static final String NAME = "SURGE";

    private final FareStrategy delegate;
    private final BigDecimal surgeMultiplier;

    public SurgeFareStrategy(FareStrategy delegate, BigDecimal surgeMultiplier) {
        this.delegate = delegate;
        this.surgeMultiplier = surgeMultiplier;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public BigDecimal calculate(FareRule rule, BigDecimal distanceKm, BigDecimal durationMinutes) {
        return delegate.calculate(rule, distanceKm, durationMinutes)
                .multiply(surgeMultiplier)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
