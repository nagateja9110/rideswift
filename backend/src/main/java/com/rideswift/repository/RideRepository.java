package com.rideswift.repository;

import com.rideswift.model.Ride;
import com.rideswift.model.RideStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideRepository extends JpaRepository<Ride, UUID> {

    List<Ride> findByPassengerIdOrderByRequestedAtDesc(UUID passengerId);

    List<Ride> findByDriverIdOrderByRequestedAtDesc(UUID driverId);

    Optional<Ride> findFirstByDriverIdAndStatusInOrderByRequestedAtDesc(
            UUID driverId, Collection<RideStatus> statuses);

    long countByStatus(RideStatus status);

    List<Ride> findByStatus(RideStatus status);

    /** Scheduled rides whose dispatch time has arrived (used by the Quartz sweeper). */
    List<Ride> findByStatusAndScheduledAtLessThanEqual(RideStatus status, java.time.Instant cutoff);

    @Query("SELECT COALESCE(SUM(r.actualFare), 0) FROM Ride r WHERE r.status = com.rideswift.model.RideStatus.COMPLETED")
    java.math.BigDecimal totalCompletedRevenue();

    /** Demand signal: ride requests whose pickup falls inside a zone since a cutoff. */
    @Query(value = """
            SELECT count(*) FROM rides r
            JOIN surge_zones z ON ST_Covers(z.area, r.pickup_location)
            WHERE z.id = :zoneId AND r.requested_at >= :since
            """, nativeQuery = true)
    long countRequestsInZoneSince(@Param("zoneId") UUID zoneId, @Param("since") java.time.Instant since);

    /** Locks the ride row for the duration of the charge/refund transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Ride r WHERE r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") UUID id);
}
