package com.rideswift.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RefundRequest(
        @NotNull UUID paymentId
) {
}
