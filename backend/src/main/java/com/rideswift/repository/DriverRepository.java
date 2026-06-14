package com.rideswift.repository;

import com.rideswift.model.Driver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByLicenseNumber(String licenseNumber);

    @Query("SELECT d.user.id FROM Driver d")
    List<UUID> findAllDriverUserIds();

    long countByVerificationStatus(com.rideswift.model.VerificationStatus status);

    List<Driver> findByVerificationStatus(com.rideswift.model.VerificationStatus status);

    /** Verified drivers as lightweight rows for the fleet simulator (no lazy associations). */
    @Query("""
            SELECT new com.rideswift.repository.FleetDriverView(d.id, d.user.id, d.available, d.currentLocation)
            FROM Driver d
            WHERE d.verificationStatus = com.rideswift.model.VerificationStatus.VERIFIED
              AND d.currentLocation IS NOT NULL
            """)
    List<FleetDriverView> findFleet();

    /** Supply signal: verified, available drivers currently located inside a zone. */
    @Query(value = """
            SELECT count(*) FROM drivers d
            JOIN surge_zones z ON ST_Covers(z.area, d.current_location)
            WHERE z.id = :zoneId
              AND d.is_available = true
              AND d.verification_status = 'VERIFIED'
            """, nativeQuery = true)
    long countAvailableDriversInZone(@Param("zoneId") UUID zoneId);

    /**
     * PostGIS nearest-driver search: verified, available drivers that own a
     * vehicle of the requested type, within {@code radiusMeters} of the pickup,
     * ordered by distance (KNN operator, backed by the GIST index). This is the
     * authoritative source and the fallback when the Redis GEO cache is empty.
     */
    @Query(value = """
            SELECT d.* FROM drivers d
            WHERE d.is_available = true
              AND d.verification_status = 'VERIFIED'
              AND d.current_location IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM vehicles v
                  WHERE v.driver_id = d.id AND v.vehicle_type = :vehicleType)
              AND ST_DWithin(
                  d.current_location,
                  ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                  :radiusMeters)
            ORDER BY d.current_location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
            LIMIT :limit
            """, nativeQuery = true)
    List<Driver> findNearbyAvailable(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("radiusMeters") double radiusMeters,
                                     @Param("vehicleType") String vehicleType,
                                     @Param("limit") int limit);

    /**
     * All verified, available drivers within {@code radiusMeters} of a point,
     * ordered by distance — for the passenger map's ambient "cars near you"
     * layer. Unlike {@link #findNearbyAvailable} this is NOT filtered by vehicle
     * type and allows a larger limit, since it is purely for display.
     */
    @Query(value = """
            SELECT d.* FROM drivers d
            WHERE d.is_available = true
              AND d.verification_status = 'VERIFIED'
              AND d.current_location IS NOT NULL
              AND ST_DWithin(
                  d.current_location,
                  ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                  :radiusMeters)
            ORDER BY d.current_location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
            LIMIT :limit
            """, nativeQuery = true)
    List<Driver> findVisibleNearby(@Param("lat") double lat,
                                   @Param("lng") double lng,
                                   @Param("radiusMeters") double radiusMeters,
                                   @Param("limit") int limit);
}
