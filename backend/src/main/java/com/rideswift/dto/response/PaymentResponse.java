package com.rideswift.dto.response;

import com.rideswift.model.Gateway;
import com.rideswift.model.Payment;
import com.rideswift.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID rideId,
        BigDecimal amount,
        BigDecimal tipAmount,
        String currency,
        Gateway gateway,
        String gatewayTransactionId,
        PaymentStatus status,
        Instant processedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getRide().getId(),
                payment.getAmount(),
                payment.getTipAmount(),
                payment.getCurrency(),
                payment.getGateway(),
                payment.getGatewayTransactionId(),
                payment.getStatus(),
                payment.getProcessedAt());
    }
}
