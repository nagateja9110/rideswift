package com.rideswift.repository;

import com.rideswift.model.Role;
import com.rideswift.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    long countByRole(Role role);

    @Query("SELECT u.id FROM User u")
    List<UUID> findAllUserIds();

    @Query("SELECT u.id FROM User u WHERE u.role = :role")
    List<UUID> findUserIdsByRole(@Param("role") Role role);
}
