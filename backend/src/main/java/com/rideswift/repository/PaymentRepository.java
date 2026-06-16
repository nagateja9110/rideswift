package com.rideswift.repository;

import com.rideswift.model.Payment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByRideId(UUID rideId);

    boolean existsByRideId(UUID rideId);

    List<Payment> findByRideIdIn(Collection<UUID> rideIds);
}
