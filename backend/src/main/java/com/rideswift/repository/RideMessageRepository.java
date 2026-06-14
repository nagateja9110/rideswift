package com.rideswift.repository;

import com.rideswift.model.RideMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideMessageRepository extends JpaRepository<RideMessage, UUID> {

    List<RideMessage> findByRideIdOrderByCreatedAtAsc(UUID rideId);
}
