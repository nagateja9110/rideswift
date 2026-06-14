package com.rideswift.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoUtils {

    public static final int SRID_WGS84 = 4326;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private GeoUtils() {
    }

    /** Builds a WGS84 point. Note JTS order is (x=longitude, y=latitude). */
    public static Point point(double latitude, double longitude) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(SRID_WGS84);
        return point;
    }

    /**
     * Builds a WGS84 polygon from a closed ring of [longitude, latitude] pairs.
     * The ring is auto-closed if the caller did not repeat the first vertex.
     */
    public static Polygon polygon(double[][] lngLatRing) {
        if (lngLatRing == null || lngLatRing.length < 3) {
            throw new IllegalArgumentException("A polygon ring needs at least 3 points");
        }
        boolean closed = java.util.Arrays.equals(lngLatRing[0], lngLatRing[lngLatRing.length - 1]);
        int size = closed ? lngLatRing.length : lngLatRing.length + 1;
        Coordinate[] coordinates = new Coordinate[size];
        for (int i = 0; i < lngLatRing.length; i++) {
            coordinates[i] = new Coordinate(lngLatRing[i][0], lngLatRing[i][1]);
        }
        if (!closed) {
            coordinates[size - 1] = new Coordinate(lngLatRing[0][0], lngLatRing[0][1]);
        }
        LinearRing ring = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(ring, null);
        polygon.setSRID(SRID_WGS84);
        return polygon;
    }

    /** Great-circle distance in kilometres between two lat/lng pairs. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
