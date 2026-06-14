package com.rideswift.config;

import com.rideswift.scheduler.DispatchSweepJob;
import com.rideswift.scheduler.FleetSimulationJob;
import com.rideswift.scheduler.MaintenanceNotificationJob;
import com.rideswift.scheduler.ScheduledRideDispatchJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail maintenanceJobDetail() {
        return JobBuilder.newJob(MaintenanceNotificationJob.class)
                .withIdentity("maintenanceNotificationJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger maintenanceJobTrigger(JobDetail maintenanceJobDetail,
                                         @Value("${rideswift.maintenance.interval-seconds:86400}")
                                         int intervalSeconds) {
        return TriggerBuilder.newTrigger()
                .forJob(maintenanceJobDetail)
                .withIdentity("maintenanceNotificationTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(intervalSeconds)
                        .repeatForever())
                .build();
    }

    @Bean
    public JobDetail scheduledRideDispatchJobDetail() {
        return JobBuilder.newJob(ScheduledRideDispatchJob.class)
                .withIdentity("scheduledRideDispatchJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger scheduledRideDispatchTrigger(JobDetail scheduledRideDispatchJobDetail,
                                                @Value("${rideswift.scheduling.sweep-seconds:30}")
                                                int sweepSeconds) {
        return TriggerBuilder.newTrigger()
                .forJob(scheduledRideDispatchJobDetail)
                .withIdentity("scheduledRideDispatchTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(sweepSeconds)
                        .repeatForever())
                .build();
    }

    @Bean
    public JobDetail dispatchSweepJobDetail() {
        return JobBuilder.newJob(DispatchSweepJob.class)
                .withIdentity("dispatchSweepJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger dispatchSweepTrigger(JobDetail dispatchSweepJobDetail,
                                        @Value("${rideswift.dispatch.sweep-seconds:5}")
                                        int sweepSeconds) {
        return TriggerBuilder.newTrigger()
                .forJob(dispatchSweepJobDetail)
                .withIdentity("dispatchSweepTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(sweepSeconds)
                        .repeatForever())
                .build();
    }

    @Bean
    public JobDetail fleetSimulationJobDetail() {
        return JobBuilder.newJob(FleetSimulationJob.class)
                .withIdentity("fleetSimulationJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger fleetSimulationTrigger(JobDetail fleetSimulationJobDetail,
                                          @Value("${rideswift.fleet.tick-seconds:2}")
                                          int tickSeconds) {
        return TriggerBuilder.newTrigger()
                .forJob(fleetSimulationJobDetail)
                .withIdentity("fleetSimulationTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(tickSeconds)
                        .repeatForever())
                .build();
    }
}
