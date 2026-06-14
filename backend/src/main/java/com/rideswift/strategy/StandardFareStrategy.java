package com.rideswift.strategy;

import com.rideswift.model.FareRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * fare = (base + perKm * distanceKm + perMinute * durationMinutes) * surgeMultiplier
 * The standard strategy applies the rule's surge multiplier as-is (1.00 by default).
 */
@Component
public class StandardFareStrategy implements FareStrategy {

    public static final String NAME = "STANDARD";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public BigDecimal calculate(FareRule rule, BigDecimal distanceKm, BigDecimal durationMinutes) {
        BigDecimal distanceComponent = rule.getPerKmRate().multiply(distanceKm);
        BigDecimal timeComponent = rule.getPerMinuteRate().multiply(durationMinutes);
        BigDecimal subtotal = rule.getBaseFare().add(distanceComponent).add(timeComponent);
        return subtotal.multiply(rule.getSurgeMultiplier()).setScale(2, RoundingMode.HALF_UP);
    }
}
