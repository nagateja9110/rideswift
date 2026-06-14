package com.rideswift.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideswift.model.FareRule;
import com.rideswift.model.VehicleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StandardFareStrategyTest {

    private final StandardFareStrategy strategy = new StandardFareStrategy();

    @Test
    void computesFareFromBaseDistanceAndTime() {
        FareRule rule = FareRule.builder()
                .vehicleType(VehicleType.ECONOMY)
                .baseFare(new BigDecimal("2.50"))
                .perKmRate(new BigDecimal("1.20"))
                .perMinuteRate(new BigDecimal("0.30"))
                .surgeMultiplier(BigDecimal.ONE)
                .build();

        // 2.50 + 1.20*10 + 0.30*20 = 2.50 + 12.00 + 6.00 = 20.50
        BigDecimal fare = strategy.calculate(rule, new BigDecimal("10"), new BigDecimal("20"));

        assertThat(fare).isEqualByComparingTo("20.50");
    }

    @Test
    void appliesSurgeMultiplier() {
        FareRule rule = FareRule.builder()
                .vehicleType(VehicleType.PREMIUM)
                .baseFare(new BigDecimal("5.00"))
                .perKmRate(new BigDecimal("2.00"))
                .perMinuteRate(new BigDecimal("0.50"))
                .surgeMultiplier(new BigDecimal("1.50"))
                .build();

        // (5 + 2*5 + 0.5*10) * 1.5 = 20 * 1.5 = 30.00
        BigDecimal fare = strategy.calculate(rule, new BigDecimal("5"), new BigDecimal("10"));

        assertThat(fare).isEqualByComparingTo("30.00");
    }
}
