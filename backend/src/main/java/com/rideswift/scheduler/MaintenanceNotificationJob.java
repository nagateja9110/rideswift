package com.rideswift.scheduler;

import com.rideswift.model.NotificationType;
import com.rideswift.repository.DriverRepository;
import com.rideswift.service.AuthService;
import com.rideswift.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that runs periodic housekeeping: reminds all drivers about
 * vehicle/document maintenance and purges dead refresh tokens. Dependencies are
 * field-injected because Quartz instantiates the job and Spring Boot's job
 * factory autowires it afterwards.
 */
public class MaintenanceNotificationJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceNotificationJob.class);

    private static final String MESSAGE =
            "Scheduled maintenance reminder: please keep your vehicle and documents up to date.";

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuthService authService;

    @Override
    public void execute(JobExecutionContext context) {
        List<UUID> driverUserIds = driverRepository.findAllDriverUserIds();
        driverUserIds.forEach(userId ->
                notificationService.create(userId, MESSAGE, NotificationType.EMAIL));

        int purged = authService.purgeStaleRefreshTokens();
        log.info("[QUARTZ] Maintenance job fired; notified {} driver(s), purged {} stale refresh token(s)",
                driverUserIds.size(), purged);
    }
}
