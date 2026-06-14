package com.rideswift.service;

import com.rideswift.dto.request.DriverRegistrationRequest;
import com.rideswift.dto.response.DriverResponse;
import com.rideswift.dto.response.NearbyDriverResponse;
import com.rideswift.exception.ConflictException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Driver;
import com.rideswift.model.NotificationType;
import com.rideswift.model.Role;
import com.rideswift.model.RideStatus;
import com.rideswift.model.User;
import com.rideswift.model.VehicleType;
import com.rideswift.model.VerificationStatus;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.RideRepository;
import com.rideswift.repository.UserRepository;
import com.rideswift.websocket.DriverLocationMessage;
import com.rideswift.websocket.RideTrackingHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

    /** Max drivers returned for the passenger map's ambient layer. */
    private static final int MAP_DRIVER_LIMIT = 25;

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final LocationService locationService;
    private final MatchingService matchingService;
    private final RideRepository rideRepository;
    private final RideTrackingHandler rideTracking;
    private final NotificationService notificationService;
    private final DriverPresenceService presence;

    public DriverService(DriverRepository driverRepository,
                         UserRepository userRepository,
                         LocationService locationService,
                         MatchingService matchingService,
                         RideRepository rideRepository,
                         RideTrackingHandler rideTracking,
                         NotificationService notificationService,
                         DriverPresenceService presence) {
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
        this.locationService = locationService;
        this.matchingService = matchingService;
        this.rideRepository = rideRepository;
        this.rideTracking = rideTracking;
        this.notificationService = notificationService;
        this.presence = presence;
    }

    /** Registers the given user as a driver. The profile starts unverified. */
    @Transactional
    public DriverResponse register(UUID userId, DriverRegistrationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (driverRepository.existsByUserId(userId)) {
            throw new ConflictException("Driver profile already exists for this user");
        }
        if (driverRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new ConflictException("License number already registered");
        }

        // Promote a plain passenger account to the DRIVER role on registration.
        if (user.getRole() == Role.PASSENGER) {
            user.setRole(Role.DRIVER);
            userRepository.save(user);
        }

        Driver driver = Driver.builder()
                .user(user)
                .licenseNumber(request.licenseNumber())
                .verificationStatus(VerificationStatus.PENDING)
                .available(false)
                .build();
        return DriverResponse.from(driverRepository.save(driver));
    }

    @Transactional(readOnly = true)
    public DriverResponse getById(UUID id) {
        return DriverResponse.from(loadDriver(id));
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> getAll() {
        return driverRepository.findAll().stream().map(DriverResponse::from).toList();
    }

    /** Admin-only: set a driver's verification status (Phase 5 onboarding flow). */
    @Transactional
    public DriverResponse setVerificationStatus(UUID id, VerificationStatus status) {
        Driver driver = loadDriver(id);
        driver.setVerificationStatus(status);
        if (status != VerificationStatus.VERIFIED) {
            // Only verified drivers may be online.
            driver.setAvailable(false);
        }
        DriverResponse response = DriverResponse.from(driverRepository.save(driver));
        notificationService.create(driver.getUser().getId(), switch (status) {
            case VERIFIED -> "Your driver application was approved — you can now go online.";
            case REJECTED -> "Your driver application was rejected. Please contact support.";
            case PENDING -> "Your driver verification is pending review.";
        }, NotificationType.EMAIL);
        return response;
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> listByVerificationStatus(VerificationStatus status) {
        return driverRepository.findByVerificationStatus(status).stream()
                .map(DriverResponse::from)
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!driverRepository.existsById(id)) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        driverRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DriverResponse getByUserId(UUID userId) {
        return DriverResponse.from(requireDriverByUser(userId));
    }

    /** Driver pushes a GPS fix; persisted to PostGIS, mirrored into Redis, and — if the
     * driver is on an active ride — streamed to that ride's passenger over WebSocket. */
    @Transactional
    public DriverResponse updateLocation(UUID userId, double latitude, double longitude) {
        Driver driver = requireDriverByUser(userId);
        Driver updated = locationService.updateLocation(driver.getId(), latitude, longitude);
        // A live client is driving this driver — keep the fleet simulator off them.
        presence.markClientActive(driver.getId());

        rideRepository.findFirstByDriverIdAndStatusInOrderByRequestedAtDesc(
                        driver.getId(), List.of(RideStatus.MATCHED, RideStatus.IN_PROGRESS))
                .ifPresent(ride -> rideTracking.sendDriverLocation(ride.getId(),
                        new DriverLocationMessage(ride.getId(), driver.getId(),
                                latitude, longitude, System.currentTimeMillis())));

        return DriverResponse.from(updated);
    }

    /** Toggles online/offline. Only verified drivers may go online. */
    @Transactional
    public DriverResponse setAvailability(UUID userId, boolean available) {
        Driver driver = requireDriverByUser(userId);
        if (available && driver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ConflictException("Only verified drivers can go online");
        }
        driver.setAvailable(available);
        Driver saved = driverRepository.save(driver);
        if (!available) {
            locationService.evict(driver.getId());
        }
        return DriverResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<NearbyDriverResponse> findNearby(double latitude, double longitude, VehicleType vehicleType) {
        return matchingService.findCandidates(latitude, longitude, vehicleType).stream()
                .map(c -> NearbyDriverResponse.from(c.driver(), c.distanceKm()))
                .toList();
    }

    /**
     * All available drivers within {@code radiusKm} of a point, for the passenger
     * map's ambient "cars near you" layer. Not gated by vehicle type and capped at
     * a display-friendly limit.
     */
    @Transactional(readOnly = true)
    public List<NearbyDriverResponse> findVisibleNearby(double latitude, double longitude, double radiusKm) {
        return driverRepository
                .findVisibleNearby(latitude, longitude, radiusKm * 1000.0, MAP_DRIVER_LIMIT)
                .stream()
                .map(d -> NearbyDriverResponse.from(d,
                        com.rideswift.util.GeoUtils.haversineKm(latitude, longitude,
                                d.getCurrentLocation().getY(), d.getCurrentLocation().getX())))
                .toList();
    }

    private Driver requireDriverByUser(UUID userId) {
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No driver profile for user: " + userId));
    }

    private Driver loadDriver(UUID id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
    }
}
