package com.rideswift.audit;

import com.rideswift.security.SecurityUtils;
import java.util.UUID;
import org.hibernate.envers.RevisionListener;

public class UserRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        RevInfo rev = (RevInfo) revisionEntity;
        UUID userId = SecurityUtils.currentUserId();
        rev.setUsername(userId != null ? userId.toString() : "system");
    }
}
