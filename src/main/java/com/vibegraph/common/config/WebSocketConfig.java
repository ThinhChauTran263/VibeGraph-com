package com.vibegraph.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.vibegraph.auth.websocket.RealtimeAccountAccessInterceptor;

/**
 * STOMP WebSocket configuration.
 * Endpoint: /ws/graph-updates
 * Topic prefix: /topic/projects/{id}/...
 *
 * <p>Allowed origins come from {@code vibegraph.cors.allowed-origins} (the same
 * {@link CorsProperties} the HTTP CORS layer uses), never a wildcard: the socket
 * streams full/incremental graph payloads for any project id, so it must honour the
 * same origin allow-list as the REST API to avoid cross-origin data exfiltration.
 *
 * TODO:
 * - Configure heartbeat
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;
    private final RealtimeAccountAccessInterceptor accountAccessInterceptor;

    public WebSocketConfig(
            CorsProperties properties,
            RealtimeAccountAccessInterceptor accountAccessInterceptor) {
        this.allowedOrigins = properties.getAllowedOrigins().toArray(String[]::new);
        this.accountAccessInterceptor = accountAccessInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(accountAccessInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(accountAccessInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/graph-updates")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }
}
