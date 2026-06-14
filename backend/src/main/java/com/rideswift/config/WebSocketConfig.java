package com.rideswift.config;

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

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Browser clients (SockJS fallback).
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
        // Plain STOMP-over-WebSocket for native/mobile clients.
        registry.addEndpoint("/ws-native").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
