package com.rideswift.payment;

import com.rideswift.model.Gateway;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Factory that resolves the {@link PaymentGateway} handler for a given provider.
 * All gateway beans are auto-discovered and indexed by their {@link Gateway}.
 */
@Component
public class PaymentGatewayFactory {

    private final Map<Gateway, PaymentGateway> gateways;

    public PaymentGatewayFactory(List<PaymentGateway> gatewayBeans) {
        this.gateways = gatewayBeans.stream()
                .collect(Collectors.toMap(PaymentGateway::gateway, Function.identity()));
    }

    public PaymentGateway forGateway(Gateway gateway) {
        PaymentGateway handler = gateways.get(gateway);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported payment gateway: " + gateway);
        }
        return handler;
    }
}
