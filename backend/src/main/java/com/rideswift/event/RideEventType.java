package com.rideswift.event;

public enum RideEventType {
    REQUESTED,   // ride created or re-assigned to a (new) driver — an incoming request
    MATCHED,     // driver accepted
    STARTED,     // trip in progress
    COMPLETED,   // trip finished
    CANCELLED,
    EXPIRED      // no driver accepted within the dispatch window
}
