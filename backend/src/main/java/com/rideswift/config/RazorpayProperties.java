package com.rideswift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials. Binds {@code rideswift.payment.razorpay}. Use TEST keys
 * ({@code rzp_test_…}) for the demo; the secret must come from the environment
 * (never committed). When {@code enabled=false} or keys are blank the Razorpay
 * endpoints return a clear error instead of attempting a live call.
 */
@ConfigurationProperties(prefix = "rideswift.payment.razorpay")
public record RazorpayProperties(
        boolean enabled,
        String keyId,
        String keySecret
) {
    public boolean configured() {
        return enabled && keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }
}
