package com.rideswift.observer;

import com.rideswift.event.RideEvent;
import com.rideswift.event.RideEventListener;
import com.rideswift.event.RideEventType;
import com.rideswift.model.NotificationType;
import com.rideswift.model.RideStatus;
import com.rideswift.service.NotificationService;
import com.rideswift.websocket.RideRequestMessage;
import com.rideswift.websocket.RideStatusMessage;
import com.rideswift.websocket.RideTrackingHandler;
import org.springframework.stereotype.Component;

/**
 * Observer that turns ride lifecycle events into persisted in-app notifications
 * and live STOMP pushes (ride status + incoming driver requests). The COMPLETED
 * branch doubles as the "payment due" trigger.
 */
@Component
public class NotificationObserver implements RideEventListener {

    private final NotificationService notificationService;
    private final RideTrackingHandler rideTracking;

    public NotificationObserver(NotificationService notificationService, RideTrackingHandler rideTracking) {
        this.notificationService = notificationService;
        this.rideTracking = rideTracking;
    }

    @Override
    public void onRideEvent(RideEvent event) {
        RideStatus status = toStatus(event.type());
        String passengerMessage = passengerMessage(event);

        notificationService.create(event.passengerUserId(), passengerMessage, NotificationType.PUSH);
        rideTracking.sendRideStatus(event.rideId(),
                new RideStatusMessage(event.rideId(), status, event.driverId(), passengerMessage));

        switch (event.type()) {
            case REQUESTED -> {
                if (event.driverUserId() != null) {
                    notificationService.create(event.driverUserId(),
                            "New ride request nearby", NotificationType.PUSH);
                    rideTracking.sendDriverRequest(event.driverId(), new RideRequestMessage(
                            event.rideId(), event.driverId(), event.vehicleType(),
                            event.pickupLatitude(), event.pickupLongitude(),
                            event.dropoffLatitude(), event.dropoffLongitude(),
                            event.pickupAddress(), event.dropoffAddress(),
                            event.fare(), event.distanceKm(), event.durationMinutes()));
                }
            }
            case COMPLETED, CANCELLED -> {
                if (event.driverUserId() != null) {
                    notificationService.create(event.driverUserId(),
                            event.type() == RideEventType.COMPLETED ? "Ride completed" : "Ride was cancelled",
                            NotificationType.PUSH);
                }
            }
            default -> { /* MATCHED / STARTED: passenger-only notification above */ }
        }
    }

    private String passengerMessage(RideEvent event) {
        return switch (event.type()) {
            case REQUESTED -> event.driverUserId() != null
                    ? "A nearby driver has been notified of your request"
                    : "Looking for a nearby driver…";
            case MATCHED -> "Your driver accepted and is on the way";
            case STARTED -> "Your ride has started";
            case COMPLETED -> "Ride complete — fare ₹" + event.fare() + " is due";
            case CANCELLED -> "Your ride was cancelled";
            case EXPIRED -> "No drivers available right now — please try again";
        };
    }

    private RideStatus toStatus(RideEventType type) {
        return switch (type) {
            case REQUESTED -> RideStatus.REQUESTED;
            case MATCHED -> RideStatus.MATCHED;
            case STARTED -> RideStatus.IN_PROGRESS;
            case COMPLETED -> RideStatus.COMPLETED;
            case CANCELLED -> RideStatus.CANCELLED;
            case EXPIRED -> RideStatus.EXPIRED;
        };
    }
}
