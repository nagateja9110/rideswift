package com.rideswift.payment;

import com.rideswift.model.Gateway;
import java.math.BigDecimal;

/**
 * Abstraction over an external payment provider. Concrete implementations are
 * selected at runtime by {@link PaymentGatewayFactory} (Factory pattern).
 */
public interface PaymentGateway {

    /** The provider this implementation handles. */
    Gateway gateway();

    ChargeResult charge(ChargeRequest request);

    RefundResult refund(String transactionId, BigDecimal amount);
}
