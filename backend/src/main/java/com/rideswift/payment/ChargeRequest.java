package com.rideswift.payment;

import java.math.BigDecimal;

public record ChargeRequest(BigDecimal amount, String currency, String referenceId) {
}
