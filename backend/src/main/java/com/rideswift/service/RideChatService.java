package com.rideswift.service;

import com.rideswift.dto.request.SendMessageRequest;
import com.rideswift.dto.response.RideMessageResponse;
import com.rideswift.dto.response.RideParticipantsResponse;
import com.rideswift.exception.ForbiddenException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Driver;
import com.rideswift.model.Ride;
import com.rideswift.model.RideMessage;
import com.rideswift.model.User;
import com.rideswift.model.Vehicle;
import com.rideswift.repository.RideMessageRepository;
import com.rideswift.repository.RideRepository;
import com.rideswift.repository.UserRepository;
import com.rideswift.repository.VehicleRepository;
import com.rideswift.security.SecurityUtils;
import com.rideswift.websocket.RideTrackingHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-ride chat + counterpart contact details for the passenger and assigned driver. */
@Service
public class RideChatService {

    private final RideRepository rideRepository;
    private final RideMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RideTrackingHandler trackingHandler;

    public RideChatService(RideRepository rideRepository,
                           RideMessageRepository messageRepository,
                           UserRepository userRepository,
                           VehicleRepository vehicleRepository,
                           RideTrackingHandler trackingHandler) {
        this.rideRepository = rideRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.trackingHandler = trackingHandler;
    }

    @Transactional(readOnly = true)
    public RideParticipantsResponse participants(UUID userId, UUID rideId) {
        Ride ride = loadRide(rideId);
        requireParticipantOrAdmin(ride, userId);

        RideParticipantsResponse.PassengerInfo passenger =
                RideParticipantsResponse.PassengerInfo.from(ride.getPassenger());

        RideParticipantsResponse.DriverInfo driver = null;
        if (ride.getDriver() != null) {
            Vehicle vehicle = vehicleForRide(ride);
            driver = RideParticipantsResponse.DriverInfo.from(ride.getDriver(), vehicle);
        }
        return new RideParticipantsResponse(passenger, driver);
    }

    @Transactional(readOnly = true)
    public List<RideMessageResponse> messages(UUID userId, UUID rideId) {
        Ride ride = loadRide(rideId);
        requireParticipantOrAdmin(ride, userId);
        return messageRepository.findByRideIdOrderByCreatedAtAsc(rideId).stream()
                .map(RideMessageResponse::from)
                .toList();
    }

    @Transactional
    public RideMessageResponse send(UUID userId, UUID rideId, SendMessageRequest req) {
        Ride ride = loadRide(rideId);
        if (!isParticipant(ride, userId)) {
            throw new ForbiddenException("You are not a participant of this ride");
        }
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        RideMessage message = messageRepository.save(RideMessage.builder()
                .ride(ride)
                .sender(sender)
                .content(req.content())
                .build());

        RideMessageResponse response = RideMessageResponse.from(message);
        trackingHandler.sendChatMessage(rideId, response);
        return response;
    }

    /** The driver's vehicle matching the ride's vehicle type, falling back to any owned vehicle. */
    private Vehicle vehicleForRide(Ride ride) {
        List<Vehicle> vehicles = vehicleRepository.findByDriverId(ride.getDriver().getId());
        return vehicles.stream()
                .filter(v -> v.getVehicleType() == ride.getVehicleType())
                .findFirst()
                .orElse(vehicles.isEmpty() ? null : vehicles.get(0));
    }

    private Ride loadRide(UUID rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
    }

    private boolean isParticipant(Ride ride, UUID userId) {
        boolean isPassenger = ride.getPassenger().getId().equals(userId);
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(userId);
        return isPassenger || isAssignedDriver;
    }

    private void requireParticipantOrAdmin(Ride ride, UUID userId) {
        if (!isParticipant(ride, userId) && !SecurityUtils.isCurrentUserAdmin()) {
            throw new ForbiddenException("You are not a participant of this ride");
        }
    }
}
