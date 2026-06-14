package com.rideswift.payment;

import com.rideswift.exception.GatewayUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wraps the external payment-gateway calls with a Resilience4j circuit breaker,
 * retry, and bulkhead (instance "paymentGateway"). When the breaker is open the
 * fallback fails fast with {@link GatewayUnavailableException}. Per the design's
 * cascading-failure mitigation, this isolates a slow/failing provider.
 */
@Component
public class ResilientGatewayExecutor {

    private static final String INSTANCE = "paymentGateway";
    private static final Logger log = LoggerFactory.getLogger(ResilientGatewayExecutor.class);

    private final PaymentOutageSimulator outage;

    public ResilientGatewayExecutor(PaymentOutageSimulator outage) {
        this.outage = outage;
    }

    @Bulkhead(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "chargeFallback")
    @Retry(name = INSTANCE)
    public ChargeResult charge(PaymentGateway gateway, ChargeRequest request) {
        failIfOutage();
        return gateway.charge(request);
    }

    @Bulkhead(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "refundFallback")
    @Retry(name = INSTANCE)
    public RefundResult refund(PaymentGateway gateway, String transactionId, BigDecimal amount) {
        failIfOutage();
        return gateway.refund(transactionId, amount);
    }

    @SuppressWarnings("unused")
    private ChargeResult chargeFallback(PaymentGateway gateway, ChargeRequest request, Throwable t) {
        log.warn("Payment charge fallback (circuit open or call failed): {}", t.toString());
        throw new GatewayUnavailableException("Payment provider temporarily unavailable");
    }

    @SuppressWarnings("unused")
    private RefundResult refundFallback(PaymentGateway gateway, String transactionId,
                                        BigDecimal amount, Throwable t) {
        log.warn("Payment refund fallback (circuit open or call failed): {}", t.toString());
        throw new GatewayUnavailableException("Payment provider temporarily unavailable");
    }

    private void failIfOutage() {
        if (outage.isDown()) {
            throw new GatewayUnavailableException("Simulated payment provider outage");
        }
    }
}
