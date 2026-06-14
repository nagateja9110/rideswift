package com.rideswift.dto.request;

import com.rideswift.model.Gateway;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record InitiatePaymentRequest(
        @NotNull java.util.UUID rideId,
        @NotNull Gateway gateway,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code")
        String currency,
        @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal tipAmount   // null = no tip
) {
}
