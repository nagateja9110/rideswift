package com.rideswift.payment;

import com.rideswift.model.Gateway;
import org.springframework.stereotype.Component;

@Component
public class RazorpayGateway extends AbstractSimulatedGateway {

    public RazorpayGateway() {
        super(Gateway.RAZORPAY, "razorpay");
    }
}
