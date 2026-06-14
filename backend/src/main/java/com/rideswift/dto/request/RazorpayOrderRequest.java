package com.rideswift.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Passenger asks the backend to open a Razorpay order for a completed ride. */
public record RazorpayOrderRequest(
        @NotNull UUID rideId,
        @DecimalMin("0.0") @DecimalMax("5000.0") BigDecimal tipAmount   // null = no tip
) {
}
