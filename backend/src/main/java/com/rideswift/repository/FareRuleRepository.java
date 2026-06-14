package com.rideswift.repository;

import com.rideswift.model.FareRule;
import com.rideswift.model.VehicleType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FareRuleRepository extends JpaRepository<FareRule, UUID> {

    @Query("""
            SELECT f FROM FareRule f
            WHERE f.vehicleType = :type
              AND f.effectiveFrom <= :at
              AND (f.effectiveTo IS NULL OR f.effectiveTo > :at)
            ORDER BY f.effectiveFrom DESC
            """)
    List<FareRule> findActive(@Param("type") VehicleType type, @Param("at") Instant at, Limit limit);
}
