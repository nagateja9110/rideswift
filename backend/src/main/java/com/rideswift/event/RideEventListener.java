package com.rideswift.event;

/** Observer in the ride-lifecycle Observer pattern. */
public interface RideEventListener {
    void onRideEvent(RideEvent event);
}
