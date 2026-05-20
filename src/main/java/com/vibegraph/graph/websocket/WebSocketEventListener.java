package com.vibegraph.graph.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket connection event listener.
 *
 * TODO:
 * - Log connect/disconnect events
 * - Track active sessions per project
 * - Cleanup on disconnect
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        // TODO: Handle connection
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        // TODO: Handle disconnection
    }
}
