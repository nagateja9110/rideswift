package com.rideswift.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link RazorpayProperties}. */
@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayConfig {
}
