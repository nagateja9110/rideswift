package com.rideswift.payment;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Admin-toggleable switch used to simulate a payment-provider outage so the
 * Resilience4j circuit breaker / retry / fallback behaviour can be exercised.
 */
@Component
public class PaymentOutageSimulator {

    private final AtomicBoolean down = new AtomicBoolean(false);

    public void setDown(boolean value) {
        down.set(value);
    }

    public boolean isDown() {
        return down.get();
    }
}
