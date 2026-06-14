package com.rideswift.dto.response;

import com.rideswift.model.Driver;
import com.rideswift.model.VerificationStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

public record DriverResponse(
        UUID id,
        UUID userId,
        String licenseNumber,
        BigDecimal rating,
        VerificationStatus verificationStatus,
        boolean available,
        Double latitude,
        Double longitude
) {
    public static DriverResponse from(Driver driver) {
        Point loc = driver.getCurrentLocation();
        return new DriverResponse(
                driver.getId(),
                driver.getUser().getId(),
                driver.getLicenseNumber(),
                driver.getRating(),
                driver.getVerificationStatus(),
                driver.isAvailable(),
                loc != null ? loc.getY() : null,
                loc != null ? loc.getX() : null);
    }
}
