package com.rideswift.model;

public enum RideOfferStatus {
    OFFERED,    // sent to the driver, awaiting response
    ACCEPTED,   // driver took the ride
    DECLINED,   // driver passed
    EXPIRED     // driver never responded in time
}
