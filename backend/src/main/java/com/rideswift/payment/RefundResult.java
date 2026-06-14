package com.rideswift.payment;

public record RefundResult(boolean success, String message) {

    public static RefundResult ok() {
        return new RefundResult(true, "refunded");
    }

    public static RefundResult failed(String message) {
        return new RefundResult(false, message);
    }
}
