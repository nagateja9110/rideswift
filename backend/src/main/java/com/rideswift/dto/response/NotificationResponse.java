package com.rideswift.dto.response;

import com.rideswift.model.Notification;
import com.rideswift.model.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String message,
        NotificationType type,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getMessage(), n.getType(), n.isRead(), n.getCreatedAt());
    }
}
