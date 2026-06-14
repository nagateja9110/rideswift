package com.rideswift.event;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Subject of the Observer pattern: fans a {@link RideEvent} out to every
 * registered {@link RideEventListener}. A failing observer is logged and
 * isolated so it cannot break the ride transaction or sibling observers.
 */
@Component
public class RideEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RideEventPublisher.class);

    private final List<RideEventListener> listeners;

    public RideEventPublisher(List<RideEventListener> listeners) {
        this.listeners = listeners;
    }

    public void publish(RideEvent event) {
        for (RideEventListener listener : listeners) {
            try {
                listener.onRideEvent(event);
            } catch (RuntimeException ex) {
                log.warn("Ride event observer {} failed for {}: {}",
                        listener.getClass().getSimpleName(), event.type(), ex.toString());
            }
        }
    }
}
