package com.rideswift.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideswift.model.FareRule;
import com.rideswift.model.VehicleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SurgeFareStrategyTest {

    private FareRule economyRule() {
        return FareRule.builder()
                .vehicleType(VehicleType.ECONOMY)
                .baseFare(new BigDecimal("2.50"))
                .perKmRate(new BigDecimal("1.20"))
                .perMinuteRate(new BigDecimal("0.30"))
                .surgeMultiplier(BigDecimal.ONE)
                .build();
    }

    @Test
    void decoratesBaseFareWithSurgeMultiplier() {
        FareRule rule = economyRule();
        // base = 2.50 + 1.20*10 + 0.30*20 = 20.50
        SurgeFareStrategy surge = new SurgeFareStrategy(new StandardFareStrategy(), new BigDecimal("1.8"));

        BigDecimal fare = surge.calculate(rule, new BigDecimal("10"), new BigDecimal("20"));

        // 20.50 * 1.8 = 36.90
        assertThat(fare).isEqualByComparingTo("36.90");
        assertThat(surge.name()).isEqualTo("SURGE");
    }

    @Test
    void surgeOfOneLeavesBaseFareUnchanged() {
        FareRule rule = economyRule();
        SurgeFareStrategy surge = new SurgeFareStrategy(new StandardFareStrategy(), BigDecimal.ONE);

        assertThat(surge.calculate(rule, new BigDecimal("10"), new BigDecimal("20")))
                .isEqualByComparingTo("20.50");
    }
}
