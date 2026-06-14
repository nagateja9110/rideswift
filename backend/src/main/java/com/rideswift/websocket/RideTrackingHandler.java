package com.rideswift.websocket;

import com.rideswift.dto.response.RideMessageResponse;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes server-side ride-tracking messages to STOMP topics that clients
 * subscribe to (see {@code WebSocketConfig}).
 */
@Component
public class RideTrackingHandler {

    private final SimpMessagingTemplate messaging;

    public RideTrackingHandler(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void sendRideStatus(UUID rideId, RideStatusMessage message) {
        messaging.convertAndSend("/topic/ride/" + rideId + "/status", message);
    }

    public void sendDriverLocation(UUID rideId, DriverLocationMessage message) {
        messaging.convertAndSend("/topic/ride/" + rideId + "/driver-location", message);
    }

    public void sendDriverRequest(UUID driverId, RideRequestMessage message) {
        messaging.convertAndSend("/topic/driver/" + driverId + "/requests", message);
    }

    public void sendChatMessage(UUID rideId, RideMessageResponse message) {
        messaging.convertAndSend("/topic/ride/" + rideId + "/messages", message);
    }
}
