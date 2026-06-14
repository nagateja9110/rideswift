package com.rideswift.scheduler;

import com.rideswift.service.RideService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz sweeper that drives the live dispatch loop: it expires offers no driver
 * answered, rolls each waiting ride to the next nearest driver, and expires rides
 * nobody accepted within the dispatch window. Polling keeps dispatch resilient
 * across restarts (the offer timers live in the DB, not in memory).
 */
public class DispatchSweepJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DispatchSweepJob.class);

    @Autowired
    private RideService rideService;

    @Override
    public void execute(JobExecutionContext context) {
        int actions = rideService.sweepDispatch();
        if (actions > 0) {
            log.info("[DISPATCH] swept {} ride(s): re-offered / expired", actions);
        }
    }
}
