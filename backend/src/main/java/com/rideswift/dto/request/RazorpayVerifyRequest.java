package com.rideswift.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Checkout callback values the frontend posts back for server-side verification. */
public record RazorpayVerifyRequest(
        @NotNull UUID paymentId,
        @NotBlank String razorpayOrderId,
        @NotBlank String razorpayPaymentId,
        @NotBlank String razorpaySignature
) {
}
