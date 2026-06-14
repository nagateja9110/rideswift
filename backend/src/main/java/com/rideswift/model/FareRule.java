package com.rideswift.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fare_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "base_fare", nullable = false)
    private BigDecimal baseFare;

    @Column(name = "per_km_rate", nullable = false)
    private BigDecimal perKmRate;

    @Column(name = "per_minute_rate", nullable = false)
    private BigDecimal perMinuteRate;

    @Column(name = "surge_multiplier", nullable = false)
    @Builder.Default
    private BigDecimal surgeMultiplier = BigDecimal.ONE;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private Instant effectiveFrom = Instant.now();

    @Column(name = "effective_to")
    private Instant effectiveTo;
}
