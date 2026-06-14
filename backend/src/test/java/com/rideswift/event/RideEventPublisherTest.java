package com.rideswift.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RideEventPublisherTest {

    private RideEvent sampleEvent() {
        return new RideEvent(RideEventType.REQUESTED, UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void notifiesEveryRegisteredObserver() {
        List<RideEventType> seenA = new ArrayList<>();
        List<RideEventType> seenB = new ArrayList<>();
        RideEventPublisher publisher = new RideEventPublisher(List.of(
                e -> seenA.add(e.type()),
                e -> seenB.add(e.type())));

        publisher.publish(sampleEvent());

        assertThat(seenA).containsExactly(RideEventType.REQUESTED);
        assertThat(seenB).containsExactly(RideEventType.REQUESTED);
    }

    @Test
    void isolatesAFailingObserverFromTheRest() {
        List<RideEventType> seen = new ArrayList<>();
        RideEventPublisher publisher = new RideEventPublisher(List.of(
                e -> { throw new RuntimeException("boom"); },
                e -> seen.add(e.type())));

        // Must not propagate the failure, and the healthy observer still runs.
        publisher.publish(sampleEvent());

        assertThat(seen).containsExactly(RideEventType.REQUESTED);
    }
}
