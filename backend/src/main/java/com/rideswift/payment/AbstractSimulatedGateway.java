package com.rideswift.payment;

import com.rideswift.model.Gateway;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulated gateway behaviour shared by the concrete providers. These stubs
 * stand in for the real SDK calls (Stripe/PayPal/Razorpay): they approve any
 * positive-amount charge and mint a provider-prefixed transaction id. Replace
 * the {@link #charge}/{@link #refund} bodies with real SDK calls when wiring a
 * live account.
 */
abstract class AbstractSimulatedGateway implements PaymentGateway {

    private final Gateway gateway;
    private final String transactionPrefix;

    protected AbstractSimulatedGateway(Gateway gateway, String transactionPrefix) {
        this.gateway = gateway;
        this.transactionPrefix = transactionPrefix;
    }

    @Override
    public Gateway gateway() {
        return gateway;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            return ChargeResult.declined("amount must be positive");
        }
        String transactionId = transactionPrefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return ChargeResult.ok(transactionId);
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount) {
        if (transactionId == null || transactionId.isBlank()) {
            return RefundResult.failed("missing transaction id");
        }
        return RefundResult.ok();
    }
}
