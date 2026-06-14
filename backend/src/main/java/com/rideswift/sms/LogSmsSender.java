package com.rideswift.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default no-gateway SMS sender: logs the message instead of sending it. Lets the
 * demo run phone-OTP login with no paid SMS provider. To send for real, add an
 * {@link SmsSender} bean for your provider (Twilio/MSG91/…) marked {@code @Primary}.
 */
@Component
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger("sms");

    @Override
    public void send(String phone, String message) {
        log.info("[SMS →{}] {}", phone, message);
    }
}
