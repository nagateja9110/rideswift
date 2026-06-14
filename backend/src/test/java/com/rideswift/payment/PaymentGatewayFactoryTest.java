package com.rideswift.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideswift.model.Gateway;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentGatewayFactoryTest {

    private final PaymentGatewayFactory factory = new PaymentGatewayFactory(
            List.of(new StripeGateway(), new PayPalGateway(), new RazorpayGateway()));

    @Test
    void resolvesEachProvider() {
        assertThat(factory.forGateway(Gateway.STRIPE)).isInstanceOf(StripeGateway.class);
        assertThat(factory.forGateway(Gateway.PAYPAL)).isInstanceOf(PayPalGateway.class);
        assertThat(factory.forGateway(Gateway.RAZORPAY)).isInstanceOf(RazorpayGateway.class);
    }

    @Test
    void chargeApprovesPositiveAmountWithProviderPrefix() {
        ChargeResult result = factory.forGateway(Gateway.STRIPE)
                .charge(new ChargeRequest(new BigDecimal("20.50"), "USD", "ride-1"));
        assertThat(result.success()).isTrue();
        assertThat(result.transactionId()).startsWith("stripe_");
    }

    @Test
    void chargeDeclinesNonPositiveAmount() {
        ChargeResult result = factory.forGateway(Gateway.PAYPAL)
                .charge(new ChargeRequest(BigDecimal.ZERO, "USD", "ride-1"));
        assertThat(result.success()).isFalse();
    }

    @Test
    void refundRequiresTransactionId() {
        assertThat(factory.forGateway(Gateway.RAZORPAY).refund("razorpay_abc", new BigDecimal("5")).success())
                .isTrue();
        assertThat(factory.forGateway(Gateway.RAZORPAY).refund(" ", new BigDecimal("5")).success())
                .isFalse();
    }
}
