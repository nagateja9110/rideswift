package com.rideswift.payment;

import com.rideswift.model.Gateway;
import org.springframework.stereotype.Component;

@Component
public class PayPalGateway extends AbstractSimulatedGateway {

    public PayPalGateway() {
        super(Gateway.PAYPAL, "paypal");
    }
}
