package com.vibegraph.auth.websocket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

/** Authenticates STOMP sessions and revalidates project access for every realtime delivery. */
@Component
@RequiredArgsConstructor
public class RealtimeAccountAccessInterceptor implements ChannelInterceptor {

    private static final String PROJECT_TOPIC_PREFIX = "/topic/projects/";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RESTRICTED_MESSAGE = "Account cannot access realtime project updates";

    private final JwtService jwtService;
    private final AccountAccessGuard accountAccessGuard;
    private final ProjectOwnershipGuard ownershipGuard;
    private final Map<String, UUID> userIdsBySession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> projectIdsBySession = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String sessionId = accessor.getSessionId();
        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            authenticateSession(sessionId, accessor);
            return message;
        }
        if (command == StompCommand.DISCONNECT) {
            removeSession(sessionId);
            return message;
        }

        String projectId = projectId(accessor.getDestination());
        if (projectId == null) {
            return message;
        }

        UUID userId = sessionId == null ? null : userIdsBySession.get(sessionId);
        if (!accountAccessGuard.canAccessRealtime(userId)) {
            return rejectOrSuppress(command);
        }
        if (command == StompCommand.SUBSCRIBE) {
            ownershipGuard.assertOwner(projectId, userId);
            projectIdsBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(projectId);
            return message;
        }
        return isAuthorizedSessionProject(sessionId, projectId) ? message : null;
    }

    private void authenticateSession(String sessionId, StompHeaderAccessor accessor) {
        if (sessionId == null) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        String token = bearerToken(accessor);
        try {
            AuthenticatedUser user = jwtService.parse(token);
            if (!accountAccessGuard.canAccessRealtime(user.id())) {
                throw new AccessDeniedException(RESTRICTED_MESSAGE);
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
            userIdsBySession.put(sessionId, user.id());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE, ex);
        }
    }

    private String bearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        return token;
    }

    private Message<?> rejectOrSuppress(StompCommand command) {
        if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        return null;
    }

    private boolean isAuthorizedSessionProject(String sessionId, String projectId) {
        return sessionId != null
                && projectIdsBySession.getOrDefault(sessionId, Set.of()).contains(projectId);
    }

    private void removeSession(String sessionId) {
        if (sessionId != null) {
            userIdsBySession.remove(sessionId);
            projectIdsBySession.remove(sessionId);
        }
    }

    private String projectId(String destination) {
        if (destination == null || !destination.startsWith(PROJECT_TOPIC_PREFIX)) {
            return null;
        }
        String remainder = destination.substring(PROJECT_TOPIC_PREFIX.length());
        int separator = remainder.indexOf('/');
        if (separator <= 0) {
            return null;
        }
        String projectId = remainder.substring(0, separator);
        String suffix = remainder.substring(separator);
        return "/updates".equals(suffix) || "/status".equals(suffix) ? projectId : null;
    }
}
