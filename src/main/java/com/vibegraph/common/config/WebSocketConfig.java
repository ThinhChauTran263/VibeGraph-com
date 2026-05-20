package com.vibegraph.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket configuration.
 * Endpoint: /ws/graph-updates
 * Topic prefix: /topic/projects/{id}/...
 *
 * TODO:
 * - Configure SockJS fallback
 * - Set allowed origins from properties
 * - Configure heartbeat
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // TODO: Configure broker
        // config.enableSimpleBroker("/topic");
        // config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // TODO: Register /ws/graph-updates endpoint
        // registry.addEndpoint("/ws/graph-updates").setAllowedOriginPatterns("*").withSockJS();
    }
}
