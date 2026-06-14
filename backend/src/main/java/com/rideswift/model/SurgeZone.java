package com.rideswift.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "surge_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurgeZone extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geography(Polygon,4326)", nullable = false)
    private Polygon area;

    @Column(name = "min_multiplier", nullable = false)
    @Builder.Default
    private BigDecimal minMultiplier = BigDecimal.ONE;

    @Column(name = "max_multiplier", nullable = false)
    @Builder.Default
    private BigDecimal maxMultiplier = new BigDecimal("3.00");

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
