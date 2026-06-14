package com.rideswift.repository;

import java.util.UUID;
import org.locationtech.jts.geom.Point;

/**
 * Lightweight projection of a driver for the fleet simulator: just the ids,
 * availability and current location, fetched eagerly so the simulator can run
 * outside a transaction without touching lazy associations.
 */
public record FleetDriverView(UUID driverId, UUID userId, boolean available, Point location) {
}
