package com.rideswift.payment;

public record ChargeResult(boolean success, String transactionId, String message) {

    public static ChargeResult ok(String transactionId) {
        return new ChargeResult(true, transactionId, "approved");
    }

    public static ChargeResult declined(String message) {
        return new ChargeResult(false, null, message);
    }
}
