package com.rideswift.strategy;

import com.rideswift.model.FareRule;
import java.math.BigDecimal;

/**
 * Pluggable fare-calculation strategy (Strategy pattern). Phase 2 ships the
 * standard strategy; surge/promotional strategies are added in later phases.
 */
public interface FareStrategy {

    /** Identifier used to select this strategy (e.g. "STANDARD"). */
    String name();

    BigDecimal calculate(FareRule rule, BigDecimal distanceKm, BigDecimal durationMinutes);
}
