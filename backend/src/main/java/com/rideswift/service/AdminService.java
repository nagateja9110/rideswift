package com.rideswift.service;

import com.rideswift.dto.request.BroadcastRequest;
import com.rideswift.dto.request.FareAdjustmentRequest;
import com.rideswift.dto.response.DashboardResponse;
import com.rideswift.dto.response.RideResponse;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.NotificationType;
import com.rideswift.model.Ride;
import com.rideswift.model.RideStatus;
import com.rideswift.model.Role;
import com.rideswift.repository.DriverRepository;
import com.rideswift.repository.RideRepository;
import com.rideswift.repository.UserRepository;
import com.rideswift.model.VerificationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final NotificationService notificationService;

    public AdminService(RideRepository rideRepository,
                        UserRepository userRepository,
                        DriverRepository driverRepository,
                        NotificationService notificationService) {
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        long active = rideRepository.countByStatus(RideStatus.REQUESTED)
                + rideRepository.countByStatus(RideStatus.MATCHED)
                + rideRepository.countByStatus(RideStatus.IN_PROGRESS);
        return new DashboardResponse(
                active,
                rideRepository.countByStatus(RideStatus.COMPLETED),
                rideRepository.countByStatus(RideStatus.CANCELLED),
                rideRepository.totalCompletedRevenue(),
                userRepository.countByRole(Role.PASSENGER),
                userRepository.countByRole(Role.DRIVER),
                driverRepository.countByVerificationStatus(VerificationStatus.PENDING));
    }

    /** Dispute resolution: override the charged fare on a ride (audited by Envers). */
    @Transactional
    public RideResponse adjustFare(UUID rideId, FareAdjustmentRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
        ride.setActualFare(request.actualFare());
        return RideResponse.from(rideRepository.save(ride));
    }

    /** Broadcasts a system notification to all users, or to a single role. */
    @Transactional
    public int broadcast(BroadcastRequest request) {
        List<UUID> recipients = request.role() == null
                ? userRepository.findAllUserIds()
                : userRepository.findUserIdsByRole(request.role());
        NotificationType type = request.type() != null ? request.type() : NotificationType.PUSH;
        recipients.forEach(userId -> notificationService.create(userId, request.message(), type));
        return recipients.size();
    }
}
