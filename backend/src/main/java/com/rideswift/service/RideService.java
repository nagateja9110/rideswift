package com.rideswift.service;

import com.rideswift.dto.request.RideRequest;
import com.rideswift.dto.response.RideResponse;
import com.rideswift.event.RideEvent;
import com.rideswift.event.RideEventPublisher;
import com.rideswift.event.RideEventType;
import com.rideswift.exception.ConflictException;
import com.rideswift.exception.ForbiddenException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Driver;
import com.rideswift.model.Ride;
import com.rideswift.model.RideOffer;
import com.rideswift.model.RideOfferStatus;
import com.rideswift.model.RideStatus;
import com.rideswift.model.User;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.RideOfferRepository;
import com.rideswift.repository.RideRepository;
import com.rideswift.repository.UserRepository;
import com.rideswift.security.SecurityUtils;
import com.rideswift.util.GeoUtils;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final RideOfferRepository rideOfferRepository;
    private final FareService fareService;
    private final MatchingService matchingService;
    private final LocationService locationService;
    private final RideEventPublisher rideEventPublisher;
    private final int offerTimeoutSeconds;
    private final int maxDispatchSeconds;
    private static final SecureRandom PIN_RANDOM = new SecureRandom();

    public RideService(RideRepository rideRepository,
                       UserRepository userRepository,
                       DriverRepository driverRepository,
                       RideOfferRepository rideOfferRepository,
                       FareService fareService,
                       MatchingService matchingService,
                       LocationService locationService,
                       RideEventPublisher rideEventPublisher,
                       @Value("${rideswift.dispatch.offer-timeout-seconds:35}") int offerTimeoutSeconds,
                       @Value("${rideswift.dispatch.max-dispatch-seconds:120}") int maxDispatchSeconds) {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.rideOfferRepository = rideOfferRepository;
        this.fareService = fareService;
        this.matchingService = matchingService;
        this.locationService = locationService;
        this.rideEventPublisher = rideEventPublisher;
        this.offerTimeoutSeconds = offerTimeoutSeconds;
        this.maxDispatchSeconds = maxDispatchSeconds;
    }

    /**
     * Passenger requests a ride. If {@code scheduledAt} is in the future the ride is
     * parked in SCHEDULED and dispatched later by the sweeper; otherwise a nearest
     * eligible driver is tentatively assigned right away.
     */
    @Transactional
    public RideResponse request(UUID passengerUserId, RideRequest req) {
        User passenger = userRepository.findById(passengerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + passengerUserId));

        FareService.FareQuote quote = fareService.quote(req.vehicleType(),
                req.pickupLatitude(), req.pickupLongitude(),
                req.dropoffLatitude(), req.dropoffLongitude());

        boolean scheduled = req.scheduledAt() != null && req.scheduledAt().isAfter(Instant.now());

        Ride ride = Ride.builder()
                .passenger(passenger)
                .vehicleType(req.vehicleType())
                .pickupLocation(GeoUtils.point(req.pickupLatitude(), req.pickupLongitude()))
                .dropoffLocation(GeoUtils.point(req.dropoffLatitude(), req.dropoffLongitude()))
                .pickupAddress(req.pickupAddress())
                .dropoffAddress(req.dropoffAddress())
                .status(scheduled ? RideStatus.SCHEDULED : RideStatus.REQUESTED)
                .scheduledAt(scheduled ? req.scheduledAt() : null)
                .requestedAt(Instant.now())
                .estimatedFare(quote.fare())
                .distanceKm(quote.distanceKm())
                .durationMinutes(quote.durationMinutes())
                .build();

        Ride saved = rideRepository.save(ride);   // persist first so offers can FK the ride
        if (!scheduled) {
            offerToNextDriver(saved);
            saved = rideRepository.save(saved);
            rideEventPublisher.publish(RideEvent.of(RideEventType.REQUESTED, saved));
        }
        return RideResponse.from(saved);
    }

    /** Dispatches scheduled rides whose time has arrived. Called by the Quartz sweeper. */
    @Transactional
    public int dispatchDueScheduledRides() {
        List<Ride> due = rideRepository
                .findByStatusAndScheduledAtLessThanEqual(RideStatus.SCHEDULED, Instant.now());
        for (Ride ride : due) {
            ride.setStatus(RideStatus.REQUESTED);
            ride.setRequestedAt(Instant.now());
            offerToNextDriver(ride);
            Ride saved = rideRepository.save(ride);
            rideEventPublisher.publish(RideEvent.of(RideEventType.REQUESTED, saved));
        }
        return due.size();
    }

    /**
     * Dispatch sweeper (Quartz). For every REQUESTED ride: roll the offer to the
     * next driver when the current one lapsed, retry when nobody is on the hook,
     * and give up (EXPIRED) once the overall dispatch window is exceeded.
     */
    @Transactional
    public int sweepDispatch() {
        Instant now = Instant.now();
        int actions = 0;
        for (Ride ride : rideRepository.findByStatus(RideStatus.REQUESTED)) {
            long ageSeconds = Duration.between(ride.getRequestedAt(), now).getSeconds();

            if (ageSeconds >= maxDispatchSeconds) {
                expireOutstandingOffers(ride, now);
                ride.setDriver(null);
                ride.setOfferExpiresAt(null);
                ride.setStatus(RideStatus.EXPIRED);
                rideEventPublisher.publish(RideEvent.of(RideEventType.EXPIRED, rideRepository.save(ride)));
                actions++;
            } else if (ride.getOfferExpiresAt() != null && now.isAfter(ride.getOfferExpiresAt())) {
                // Outstanding offer lapsed (driver never responded) → roll to the next.
                expireOutstandingOffers(ride, now);
                boolean offered = offerToNextDriver(ride);
                Ride saved = rideRepository.save(ride);
                if (offered) {
                    rideEventPublisher.publish(RideEvent.of(RideEventType.REQUESTED, saved));
                }
                actions++;
            } else if (ride.getDriver() == null && ride.getOfferExpiresAt() == null) {
                // Nobody is currently on the hook (e.g. all candidates were busy) → retry.
                if (offerToNextDriver(ride)) {
                    rideEventPublisher.publish(RideEvent.of(RideEventType.REQUESTED, rideRepository.save(ride)));
                    actions++;
                }
            }
        }
        return actions;
    }

    /**
     * Offers the ride to the nearest eligible driver who has not already been
     * offered it. Records a {@link RideOffer} with an expiry and points the ride
     * at that driver; leaves the ride driverless if no fresh candidate exists.
     */
    private boolean offerToNextDriver(Ride ride) {
        double lat = ride.getPickupLocation().getY();
        double lng = ride.getPickupLocation().getX();
        Set<UUID> alreadyOffered = new HashSet<>(rideOfferRepository.findOfferedDriverIds(ride.getId()));

        Driver next = matchingService.findCandidates(lat, lng, ride.getVehicleType()).stream()
                .map(MatchingService.Candidate::driver)
                .filter(d -> !alreadyOffered.contains(d.getId()))
                .findFirst()
                .orElse(null);

        if (next == null) {
            ride.setDriver(null);
            ride.setOfferExpiresAt(null);
            return false;
        }

        Instant expiresAt = Instant.now().plusSeconds(offerTimeoutSeconds);
        rideOfferRepository.save(RideOffer.builder()
                .ride(ride)
                .driver(next)
                .status(RideOfferStatus.OFFERED)
                .offeredAt(Instant.now())
                .expiresAt(expiresAt)
                .build());
        ride.setDriver(next);
        ride.setOfferExpiresAt(expiresAt);
        return true;
    }

    /** Marks any still-outstanding offers on a ride as EXPIRED. */
    private void expireOutstandingOffers(Ride ride, Instant now) {
        for (RideOffer offer : rideOfferRepository.findByRideIdAndStatus(ride.getId(), RideOfferStatus.OFFERED)) {
            offer.setStatus(RideOfferStatus.EXPIRED);
            offer.setRespondedAt(now);
            rideOfferRepository.save(offer);
        }
    }

    /** Assigned driver accepts: REQUESTED -> MATCHED, driver goes off the market. */
    @Transactional
    public RideResponse accept(UUID driverUserId, UUID rideId) {
        Driver driver = requireDriver(driverUserId);
        Ride ride = loadRide(rideId);
        requireAssignedDriver(ride, driver);
        requireTransition(ride, RideStatus.MATCHED);

        // The offer must still be the live one — not lapsed/rolled to another driver.
        RideOffer offer = rideOfferRepository
                .findFirstByRideIdAndDriverIdAndStatus(rideId, driver.getId(), RideOfferStatus.OFFERED)
                .orElseThrow(() -> new ConflictException("This ride offer is no longer available"));
        Instant now = Instant.now();
        if (now.isAfter(offer.getExpiresAt())) {
            throw new ConflictException("This ride offer has expired");
        }
        offer.setStatus(RideOfferStatus.ACCEPTED);
        offer.setRespondedAt(now);
        rideOfferRepository.save(offer);

        ride.setStatus(RideStatus.MATCHED);
        ride.setOfferExpiresAt(null);
        ride.setPickupPin(String.format("%04d", PIN_RANDOM.nextInt(10000)));
        driver.setAvailable(false);
        driverRepository.save(driver);
        locationService.evict(driver.getId());
        Ride saved = rideRepository.save(ride);
        rideEventPublisher.publish(RideEvent.of(RideEventType.MATCHED, saved));
        // Driver's view — hide the PIN; the passenger reads it out at pickup.
        return RideResponse.from(saved, false);
    }

    /** Assigned driver declines: marks their offer DECLINED and rolls to the next driver. */
    @Transactional
    public RideResponse decline(UUID driverUserId, UUID rideId) {
        Driver driver = requireDriver(driverUserId);
        Ride ride = loadRide(rideId);

        rideOfferRepository
                .findFirstByRideIdAndDriverIdAndStatus(rideId, driver.getId(), RideOfferStatus.OFFERED)
                .ifPresent(offer -> {
                    offer.setStatus(RideOfferStatus.DECLINED);
                    offer.setRespondedAt(Instant.now());
                    rideOfferRepository.save(offer);
                });

        // Only re-dispatch if this driver is the one currently holding a still-open request;
        // a stale decline (already rolled to someone else) just records the decline.
        if (ride.getStatus() == RideStatus.REQUESTED
                && ride.getDriver() != null && ride.getDriver().getId().equals(driver.getId())) {
            boolean offered = offerToNextDriver(ride);
            Ride saved = rideRepository.save(ride);
            if (offered) {
                rideEventPublisher.publish(RideEvent.of(RideEventType.REQUESTED, saved));
            }
            return RideResponse.from(saved);
        }
        return RideResponse.from(ride);
    }

    /** Assigned driver starts the trip: MATCHED -> IN_PROGRESS, after PIN verification. */
    @Transactional
    public RideResponse start(UUID driverUserId, UUID rideId, String pin) {
        Driver driver = requireDriver(driverUserId);
        Ride ride = loadRide(rideId);
        requireAssignedDriver(ride, driver);
        requireTransition(ride, RideStatus.IN_PROGRESS);
        if (ride.getPickupPin() != null && !ride.getPickupPin().equals(pin)) {
            throw new IllegalArgumentException("Incorrect pickup PIN");
        }

        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        Ride saved = rideRepository.save(ride);
        rideEventPublisher.publish(RideEvent.of(RideEventType.STARTED, saved));
        return RideResponse.from(saved, false);
    }

    /** Assigned driver completes the trip: IN_PROGRESS -> COMPLETED, fare finalised. */
    @Transactional
    public RideResponse complete(UUID driverUserId, UUID rideId) {
        Driver driver = requireDriver(driverUserId);
        Ride ride = loadRide(rideId);
        requireAssignedDriver(ride, driver);
        requireTransition(ride, RideStatus.COMPLETED);

        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(Instant.now());
        // Phase 2 has no live trip tracking, so the estimate is the charged fare.
        ride.setActualFare(ride.getEstimatedFare());
        driver.setAvailable(true);
        driverRepository.save(driver);
        Ride saved = rideRepository.save(ride);
        rideEventPublisher.publish(RideEvent.of(RideEventType.COMPLETED, saved));
        return RideResponse.from(saved, false);
    }

    /** Passenger or assigned driver cancels a ride that has not yet started. */
    @Transactional
    public RideResponse cancel(UUID userId, UUID rideId) {
        Ride ride = loadRide(rideId);
        boolean isPassenger = ride.getPassenger().getId().equals(userId);
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(userId);
        if (!isPassenger && !isAssignedDriver && !SecurityUtils.isCurrentUserAdmin()) {
            throw new ForbiddenException("You are not a participant of this ride");
        }
        requireTransition(ride, RideStatus.CANCELLED);

        ride.setStatus(RideStatus.CANCELLED);
        if (ride.getDriver() != null) {
            Driver driver = ride.getDriver();
            driver.setAvailable(true);
            driverRepository.save(driver);
        }
        Ride saved = rideRepository.save(ride);
        rideEventPublisher.publish(RideEvent.of(RideEventType.CANCELLED, saved));
        return RideResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RideResponse get(UUID userId, UUID rideId) {
        Ride ride = loadRide(rideId);
        boolean isPassenger = ride.getPassenger().getId().equals(userId);
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(userId);
        if (!isPassenger && !isAssignedDriver && !SecurityUtils.isCurrentUserAdmin()) {
            throw new ForbiddenException("You are not a participant of this ride");
        }
        return RideResponse.from(ride, isPassenger);
    }

    /** Rides where the user is the passenger or the assigned driver, newest first. */
    @Transactional(readOnly = true)
    public List<RideResponse> history(UUID userId) {
        Map<UUID, Ride> merged = new LinkedHashMap<>();
        rideRepository.findByPassengerIdOrderByRequestedAtDesc(userId)
                .forEach(r -> merged.put(r.getId(), r));
        driverRepository.findByUserId(userId).ifPresent(driver ->
                rideRepository.findByDriverIdOrderByRequestedAtDesc(driver.getId())
                        .forEach(r -> merged.put(r.getId(), r)));
        return merged.values().stream()
                .sorted(Comparator.comparing(Ride::getRequestedAt).reversed())
                .map(r -> RideResponse.from(r, r.getPassenger().getId().equals(userId)))
                .toList();
    }

    private Driver requireDriver(UUID userId) {
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Caller is not a registered driver"));
    }

    private Ride loadRide(UUID rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
    }

    private void requireAssignedDriver(Ride ride, Driver driver) {
        if (ride.getDriver() == null || !ride.getDriver().getId().equals(driver.getId())) {
            throw new ForbiddenException("Ride is not assigned to you");
        }
    }

    private void requireTransition(Ride ride, RideStatus target) {
        if (!ride.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                    "Illegal ride transition: " + ride.getStatus() + " -> " + target);
        }
    }
}
