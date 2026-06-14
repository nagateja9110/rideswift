package com.rideswift.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Dedicated {@link RestClient} for the OSRM routing server, with short timeouts. */
@Configuration
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingConfig {

    @Bean
    public RestClient routingRestClient(RoutingProperties props) {
        Duration timeout = Duration.ofMillis(props.timeoutMillis());
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(props.baseUrl())
                .build();
    }
}
