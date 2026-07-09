package com.rideswift.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket setup for live ride tracking. Clients connect to {@code /ws}
 * (SockJS fallback) and SUBSCRIBE to server-pushed topics:
 * <ul>
 *   <li>{@code /topic/ride/{rideId}/status} — ride lifecycle updates</li>
 *   <li>{@code /topic/ride/{rideId}/driver-location} — live driver GPS</li>
 *   <li>{@code /topic/driver/{driverId}/requests} — incoming ride requests</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Same allowed origins as the REST CORS config; defaults to "*" for the open demo.
    @Value("${rideswift.security.cors.allowed-origin-patterns:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Browser clients (SockJS fallback). setSessionCookieNeeded(false) is required
        // for CROSS-ORIGIN deploys (static frontend + separate API host): otherwise
        // sockjs-client sends its handshake with credentials, which the browser blocks
        // against our `Access-Control-Allow-Origin: *` (no allow-credentials) → the
        // SockJS connection fails even though raw WebSocket works.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS().setSessionCookieNeeded(false);
        // Plain STOMP-over-WebSocket for native/mobile clients.
        registry.addEndpoint("/ws-native").setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
