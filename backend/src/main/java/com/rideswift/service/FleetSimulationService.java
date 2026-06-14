package com.rideswift.service;

import com.rideswift.model.Ride;
import com.rideswift.model.RideStatus;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.FleetDriverView;
import com.rideswift.repository.RideRepository;
import com.rideswift.util.GeoUtils;
import com.rideswift.websocket.DriverLocationMessage;
import com.rideswift.websocket.RideTrackingHandler;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Server-side driver fleet so the demo feels alive without anyone opening a driver
 * dashboard. Each tick it nudges every simulated driver:
 *  - idle drivers wander gently around the city centre (keeps the map alive and
 *    matching reliable),
 *  - a driver who has just been offered a ride auto-accepts (standing in for a
 *    human tapping "Accept"), then drives to the pickup, auto-starts with the PIN,
 *    drives to the drop-off and auto-completes.
 *
 * It yields to real driver apps: any driver whose dashboard is pushing live
 * location ({@link DriverPresenceService}) is skipped, so a human always wins.
 * Payment stays manual — the simulator never pays.
 */
@Service
public class FleetSimulationService {

    private static final Logger log = LoggerFactory.getLogger(FleetSimulationService.class);
    private static final List<RideStatus> ACTIVE =
            List.of(RideStatus.REQUESTED, RideStatus.MATCHED, RideStatus.IN_PROGRESS);

    private final DriverRepository driverRepository;
    private final RideRepository rideRepository;
    private final RideService rideService;
    private final LocationService locationService;
    private final DriverPresenceService presence;
    private final RideTrackingHandler rideTracking;
    private final Random rnd = new Random();

    private final boolean enabled;
    private final double stepMeters;
    private final double arriveMeters;
    private final double baseLat;
    private final double baseLng;
    private final double wanderRadiusDeg;

    public FleetSimulationService(DriverRepository driverRepository,
                                  RideRepository rideRepository,
                                  RideService rideService,
                                  LocationService locationService,
                                  DriverPresenceService presence,
                                  RideTrackingHandler rideTracking,
                                  @Value("${rideswift.fleet.enabled:true}") boolean enabled,
                                  @Value("${rideswift.fleet.step-meters:200}") double stepMeters,
                                  @Value("${rideswift.fleet.arrive-threshold-meters:120}") double arriveMeters,
                                  @Value("${rideswift.fleet.base-lat:13.0827}") double baseLat,
                                  @Value("${rideswift.fleet.base-lng:80.2707}") double baseLng,
                                  @Value("${rideswift.fleet.wander-radius-deg:0.04}") double wanderRadiusDeg) {
        this.driverRepository = driverRepository;
        this.rideRepository = rideRepository;
        this.rideService = rideService;
        this.locationService = locationService;
        this.presence = presence;
        this.rideTracking = rideTracking;
        this.enabled = enabled;
        this.stepMeters = stepMeters;
        this.arriveMeters = arriveMeters;
        this.baseLat = baseLat;
        this.baseLng = baseLng;
        this.wanderRadiusDeg = wanderRadiusDeg;
    }

    /** One simulation step over the whole fleet. Called by the Quartz tick job. */
    public void tick() {
        if (!enabled) {
            return;
        }
        for (FleetDriverView d : driverRepository.findFleet()) {
            if (presence.isClientActive(d.driverId())) {
                continue;   // a real driver dashboard is driving this one
            }
            try {
                Optional<Ride> active = rideRepository
                        .findFirstByDriverIdAndStatusInOrderByRequestedAtDesc(d.driverId(), ACTIVE);
                if (active.isPresent()) {
                    handleActiveRide(d, active.get());
                } else if (d.available()) {
                    wander(d);
                }
            } catch (RuntimeException ex) {
                log.debug("Fleet tick skipped driver {}: {}", d.driverId(), ex.toString());
            }
        }
    }

    private void handleActiveRide(FleetDriverView d, Ride ride) {
        double curLat = d.location().getY();
        double curLng = d.location().getX();
        switch (ride.getStatus()) {
            case REQUESTED -> safe(() -> rideService.accept(d.userId(), ride.getId()), "accept");
            case MATCHED -> driveToTarget(d, ride, curLat, curLng,
                    ride.getPickupLocation().getY(), ride.getPickupLocation().getX(),
                    () -> safe(() -> rideService.start(d.userId(), ride.getId(), ride.getPickupPin()), "start"));
            case IN_PROGRESS -> driveToTarget(d, ride, curLat, curLng,
                    ride.getDropoffLocation().getY(), ride.getDropoffLocation().getX(),
                    () -> safe(() -> rideService.complete(d.userId(), ride.getId()), "complete"));
            default -> { /* nothing to do */ }
        }
    }

    /** Steps the driver toward (tLat,tLng); runs {@code onArrival} once within range. */
    private void driveToTarget(FleetDriverView d, Ride ride, double curLat, double curLng,
                               double tLat, double tLng, Runnable onArrival) {
        double distMeters = GeoUtils.haversineKm(curLat, curLng, tLat, tLng) * 1000;
        if (distMeters <= arriveMeters) {
            moveAndBroadcast(d, ride, tLat, tLng);
            onArrival.run();
            return;
        }
        double frac = stepMeters / distMeters;
        moveAndBroadcast(d, ride,
                curLat + (tLat - curLat) * frac,
                curLng + (tLng - curLng) * frac);
    }

    /**
     * Persist the simulated driver's new position AND push it to the passenger's live
     * map — mirrors what {@code DriverService.updateLocation} does for a real driver, so
     * a fleet-driven ride animates on the rider's screen too.
     */
    private void moveAndBroadcast(FleetDriverView d, Ride ride, double lat, double lng) {
        locationService.updateLocation(d.driverId(), lat, lng);
        rideTracking.sendDriverLocation(ride.getId(),
                new DriverLocationMessage(ride.getId(), d.driverId(), lat, lng, System.currentTimeMillis()));
    }

    private void wander(FleetDriverView d) {
        double lat = d.location().getY() + (rnd.nextDouble() - 0.5) * 0.004;
        double lng = d.location().getX() + (rnd.nextDouble() - 0.5) * 0.004;
        lat = clamp(lat, baseLat - wanderRadiusDeg, baseLat + wanderRadiusDeg);
        lng = clamp(lng, baseLng - wanderRadiusDeg, baseLng + wanderRadiusDeg);
        locationService.updateLocation(d.driverId(), lat, lng);
    }

    private void safe(Runnable action, String what) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            // Expected sometimes (offer already rolled, race with a human) — debug only.
            log.debug("Fleet auto-{} failed: {}", what, ex.toString());
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
