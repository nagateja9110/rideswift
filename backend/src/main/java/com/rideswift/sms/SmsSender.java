package com.rideswift.sms;

/**
 * Sends an SMS. Swap the implementation for a real provider (Twilio, MSG91, etc.)
 * by adding a bean and pointing {@code rideswift.sms.provider} at it; the default
 * {@link LogSmsSender} just logs the message so the demo works without a gateway.
 */
public interface SmsSender {
    void send(String phone, String message);
}
