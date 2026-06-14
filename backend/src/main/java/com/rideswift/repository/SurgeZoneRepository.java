package com.rideswift.repository;

import com.rideswift.model.SurgeZone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurgeZoneRepository extends JpaRepository<SurgeZone, UUID> {

    List<SurgeZone> findByActiveTrue();

    /** The active zone whose polygon covers the given point, if any. */
    @Query(value = """
            SELECT * FROM surge_zones z
            WHERE z.active = true
              AND ST_Covers(z.area, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)
            LIMIT 1
            """, nativeQuery = true)
    Optional<SurgeZone> findContaining(@Param("lat") double lat, @Param("lng") double lng);
}
