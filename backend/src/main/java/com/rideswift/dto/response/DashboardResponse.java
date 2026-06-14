package com.rideswift.dto.response;

import java.math.BigDecimal;

public record DashboardResponse(
        long activeRides,
        long completedRides,
        long cancelledRides,
        BigDecimal totalRevenue,
        long passengerCount,
        long driverCount,
        long pendingDriverVerifications
) {
}
