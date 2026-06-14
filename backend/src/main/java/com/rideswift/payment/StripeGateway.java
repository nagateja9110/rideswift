package com.rideswift.payment;

import com.rideswift.model.Gateway;
import org.springframework.stereotype.Component;

@Component
public class StripeGateway extends AbstractSimulatedGateway {

    public StripeGateway() {
        super(Gateway.STRIPE, "stripe");
    }
}
