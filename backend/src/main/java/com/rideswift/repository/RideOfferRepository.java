package com.rideswift.repository;

import com.rideswift.model.RideOffer;
import com.rideswift.model.RideOfferStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideOfferRepository extends JpaRepository<RideOffer, UUID> {

    /** Driver ids that have already been offered this ride (any outcome) — never re-offer to them. */
    @Query("SELECT o.driver.id FROM RideOffer o WHERE o.ride.id = :rideId")
    List<UUID> findOfferedDriverIds(@Param("rideId") UUID rideId);

    /** The current outstanding offer for a ride+driver, if any. */
    Optional<RideOffer> findFirstByRideIdAndDriverIdAndStatus(
            UUID rideId, UUID driverId, RideOfferStatus status);

    List<RideOffer> findByRideIdAndStatus(UUID rideId, RideOfferStatus status);
}
