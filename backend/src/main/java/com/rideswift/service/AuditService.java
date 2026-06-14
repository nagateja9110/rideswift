package com.rideswift.service;

import com.rideswift.audit.RevInfo;
import com.rideswift.dto.response.RideAuditEntry;
import com.rideswift.model.Ride;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads Hibernate Envers history. Exposes the full revision trail of a ride —
 * every lifecycle/fare change, with the revision timestamp and acting user.
 */
@Service
public class AuditService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<RideAuditEntry> rideHistory(UUID rideId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<Number> revisions = reader.getRevisions(Ride.class, rideId);

        List<RideAuditEntry> entries = new ArrayList<>(revisions.size());
        for (Number rev : revisions) {
            Ride snapshot = reader.find(Ride.class, rideId, rev);
            RevInfo info = reader.findRevision(RevInfo.class, rev);
            entries.add(new RideAuditEntry(
                    rev.intValue(),
                    snapshot.getStatus(),
                    snapshot.getEstimatedFare(),
                    snapshot.getActualFare(),
                    info.getUsername(),
                    Instant.ofEpochMilli(info.getTimestamp())));
        }
        return entries;
    }
}
