package com.rideswift.model;

import java.util.Set;

public enum RideStatus {
    SCHEDULED,
    REQUESTED,
    MATCHED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED;   // no driver accepted within the dispatch window

    /** Allowed forward transitions of the ride lifecycle state machine. */
    public boolean canTransitionTo(RideStatus target) {
        return switch (this) {
            case SCHEDULED -> Set.of(REQUESTED, CANCELLED).contains(target);
            case REQUESTED -> Set.of(MATCHED, CANCELLED, EXPIRED).contains(target);
            case MATCHED -> Set.of(IN_PROGRESS, CANCELLED).contains(target);
            case IN_PROGRESS -> target == COMPLETED;
            case COMPLETED, CANCELLED, EXPIRED -> false;
        };
    }
}
