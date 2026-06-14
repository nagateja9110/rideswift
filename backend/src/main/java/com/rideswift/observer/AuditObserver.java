package com.rideswift.observer;

import com.rideswift.event.RideEvent;
import com.rideswift.event.RideEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Observer that records a structured log line for each ride lifecycle event.
 * (Entity-state history is captured separately by Hibernate Envers.)
 */
@Component
public class AuditObserver implements RideEventListener {

    private static final Logger log = LoggerFactory.getLogger("ride-audit");

    @Override
    public void onRideEvent(RideEvent event) {
        log.info("[RIDE-EVENT] type={} ride={} passenger={} driver={} fare={}",
                event.type(), event.rideId(), event.passengerUserId(), event.driverId(), event.fare());
    }
}
