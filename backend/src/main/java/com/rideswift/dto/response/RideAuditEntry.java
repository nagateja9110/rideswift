package com.rideswift.dto.response;

import com.rideswift.model.RideStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record RideAuditEntry(
        int revision,
        RideStatus status,
        BigDecimal estimatedFare,
        BigDecimal actualFare,
        String changedBy,
        Instant changedAt
) {
}
