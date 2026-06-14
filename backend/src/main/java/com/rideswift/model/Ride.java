package com.rideswift.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "rides")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride extends BaseEntity {

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    private User passenger;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @NotAudited
    @Column(name = "pickup_location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point pickupLocation;

    @NotAudited
    @Column(name = "dropoff_location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point dropoffLocation;

    @NotAudited
    @Column(name = "pickup_address")
    private String pickupAddress;

    @NotAudited
    @Column(name = "dropoff_address")
    private String dropoffAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RideStatus status = RideStatus.REQUESTED;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "estimated_fare")
    private BigDecimal estimatedFare;

    @Column(name = "actual_fare")
    private BigDecimal actualFare;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // 4-digit pickup verification PIN, generated on match. Not audited (transient secret).
    @NotAudited
    @Column(name = "pickup_pin")
    private String pickupPin;

    // Set when the passenger books for later; the ride sits in SCHEDULED until then.
    @NotAudited
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    // When the current outstanding dispatch offer lapses; the sweeper then rolls
    // the ride to the next driver. Transient dispatch state, not audited.
    @NotAudited
    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;
}
