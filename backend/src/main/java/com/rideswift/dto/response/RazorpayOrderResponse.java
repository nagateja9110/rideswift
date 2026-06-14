package com.rideswift.dto.response;

import java.util.UUID;

/**
 * Everything the frontend Razorpay Checkout needs to open the payment popup.
 * {@code amountPaise} is the charge in the smallest currency unit (₹1 = 100 paise),
 * as Razorpay expects.
 */
public record RazorpayOrderResponse(
        UUID paymentId,
        String keyId,
        String orderId,
        long amountPaise,
        String currency,
        String name,
        String description,
        String prefillName,
        String prefillEmail,
        String prefillContact
) {
}
