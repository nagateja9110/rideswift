package com.rideswift.scheduler;

import com.rideswift.service.FleetSimulationService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/** Drives the server-side driver fleet one step per tick. */
public class FleetSimulationJob implements Job {

    @Autowired
    private FleetSimulationService fleetSimulationService;

    @Override
    public void execute(JobExecutionContext context) {
        fleetSimulationService.tick();
    }
}
