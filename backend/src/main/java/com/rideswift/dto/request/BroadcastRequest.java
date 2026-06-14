package com.rideswift.dto.request;

import com.rideswift.model.NotificationType;
import com.rideswift.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BroadcastRequest(
        @NotBlank @Size(max = 500) String message,
        Role role,              // null = all users
        NotificationType type   // null = PUSH
) {
}
