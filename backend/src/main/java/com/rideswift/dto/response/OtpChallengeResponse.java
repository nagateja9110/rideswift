package com.rideswift.dto.response;

/**
 * Reply to an OTP request. {@code devCode} is populated only when
 * {@code rideswift.otp.expose-code=true} (demo mode with no SMS gateway), so the
 * UI can show the code; in production it is null and the code arrives by SMS.
 */
public record OtpChallengeResponse(
        String phone,
        String message,
        String devCode
) {
}
