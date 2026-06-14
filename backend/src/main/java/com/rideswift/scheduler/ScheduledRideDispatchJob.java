package com.rideswift.scheduler;

import com.rideswift.service.RideService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz sweeper that dispatches scheduled (book-for-later) rides once their time
 * arrives. Polling the DB each tick (rather than scheduling a per-ride Quartz job)
 * keeps dispatch resilient across restarts even with the in-memory job store.
 */
public class ScheduledRideDispatchJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRideDispatchJob.class);

    @Autowired
    private RideService rideService;

    @Override
    public void execute(JobExecutionContext context) {
        int dispatched = rideService.dispatchDueScheduledRides();
        if (dispatched > 0) {
            log.info("[QUARTZ] Dispatched {} scheduled ride(s) to matching", dispatched);
        }
    }
}
