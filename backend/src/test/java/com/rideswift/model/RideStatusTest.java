package com.rideswift.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RideStatusTest {

    @Test
    void allowsTheHappyPath() {
        assertThat(RideStatus.REQUESTED.canTransitionTo(RideStatus.MATCHED)).isTrue();
        assertThat(RideStatus.MATCHED.canTransitionTo(RideStatus.IN_PROGRESS)).isTrue();
        assertThat(RideStatus.IN_PROGRESS.canTransitionTo(RideStatus.COMPLETED)).isTrue();
    }

    @Test
    void allowsCancellationBeforeStart() {
        assertThat(RideStatus.REQUESTED.canTransitionTo(RideStatus.CANCELLED)).isTrue();
        assertThat(RideStatus.MATCHED.canTransitionTo(RideStatus.CANCELLED)).isTrue();
    }

    @Test
    void scheduledRideDispatchesOrCancels() {
        // A booked-for-later ride is dispatched to matching (REQUESTED) or cancelled.
        assertThat(RideStatus.SCHEDULED.canTransitionTo(RideStatus.REQUESTED)).isTrue();
        assertThat(RideStatus.SCHEDULED.canTransitionTo(RideStatus.CANCELLED)).isTrue();
        // It cannot jump straight past matching.
        assertThat(RideStatus.SCHEDULED.canTransitionTo(RideStatus.MATCHED)).isFalse();
        assertThat(RideStatus.SCHEDULED.canTransitionTo(RideStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void rejectsIllegalTransitions() {
        assertThat(RideStatus.IN_PROGRESS.canTransitionTo(RideStatus.CANCELLED)).isFalse();
        assertThat(RideStatus.REQUESTED.canTransitionTo(RideStatus.IN_PROGRESS)).isFalse();
        assertThat(RideStatus.COMPLETED.canTransitionTo(RideStatus.MATCHED)).isFalse();
        assertThat(RideStatus.CANCELLED.canTransitionTo(RideStatus.REQUESTED)).isFalse();
    }
}
