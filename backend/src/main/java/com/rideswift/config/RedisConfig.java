package com.rideswift.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Redis is used for the driver-location cache and GEO nearest-driver lookup.
 * Spring Boot auto-configures {@code StringRedisTemplate} from
 * {@code spring.data.redis.*}; this class only enables the ride-flow properties.
 *
 * <p>Key conventions:
 * <ul>
 *   <li>{@code drivers:geo} — GEO set of online driver positions (member = driverId)</li>
 *   <li>{@code driver:location:{driverId}} — last fix "{lat},{lng},{epochMillis}", TTL 10s</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(RideswiftProperties.class)
public class RedisConfig {
}
