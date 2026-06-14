package com.rideswift.dto.response;

import com.rideswift.model.RideMessage;
import java.time.Instant;
import java.util.UUID;

public record RideMessageResponse(
        UUID id,
        UUID rideId,
        UUID senderId,
        String senderName,
        String content,
        Instant sentAt
) {
    public static RideMessageResponse from(RideMessage message) {
        return new RideMessageResponse(
                message.getId(),
                message.getRide().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getContent(),
                message.getCreatedAt());
    }
}
