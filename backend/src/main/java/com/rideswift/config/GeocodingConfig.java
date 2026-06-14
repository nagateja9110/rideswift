package com.rideswift.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires a dedicated {@link RestClient} for the Nominatim geocoding proxy, with the
 * mandated User-Agent header and short connect/read timeouts so a slow upstream
 * never blocks the booking flow.
 */
@Configuration
@EnableConfigurationProperties(GeocodingProperties.class)
public class GeocodingConfig {

    @Bean
    public RestClient geocodingRestClient(GeocodingProperties props) {
        Duration timeout = Duration.ofMillis(props.timeoutMillis());
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(props.baseUrl())
                .defaultHeader("User-Agent", props.userAgent())
                .build();
    }
}
