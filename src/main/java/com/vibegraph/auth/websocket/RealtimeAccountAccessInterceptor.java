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
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.repository.FeedbackReportRepository;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.auth.service.RefreshSessionService;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;

import io.jsonwebtoken.JwtException;
/** Authenticates STOMP sessions and revalidates project access for every realtime delivery. */
@Component
public class RealtimeAccountAccessInterceptor implements ChannelInterceptor {

    private static final String PROJECT_TOPIC_PREFIX = "/topic/projects/";
    private static final String REPORT_TOPIC_PREFIX = "/topic/reports/";
    private static final String ADMIN_TOPIC_PREFIX = "/topic/admin/";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RESTRICTED_MESSAGE = "Account cannot access realtime updates";

    private final JwtService jwtService;
    private final AccountAccessGuard accountAccessGuard;
    private final ProjectOwnershipGuard ownershipGuard;
    private final FeedbackReportRepository feedbackReportRepository;
    private final RefreshSessionService refreshSessionService;
    private final Map<String, UUID> userIdsBySession = new ConcurrentHashMap<>();
    private final Map<String, UUID> authSessionIdsBySession = new ConcurrentHashMap<>();
    private final Map<String, Boolean> adminSessionsBySession = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> projectIdsBySession = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> reportIdsBySession = new ConcurrentHashMap<>();

    @Autowired
    public RealtimeAccountAccessInterceptor(
            JwtService jwtService,
            AccountAccessGuard accountAccessGuard,
            ProjectOwnershipGuard ownershipGuard,
            FeedbackReportRepository feedbackReportRepository,
            RefreshSessionService refreshSessionService) {
        this.jwtService = jwtService;
        this.accountAccessGuard = accountAccessGuard;
        this.ownershipGuard = ownershipGuard;
        this.feedbackReportRepository = feedbackReportRepository;
        this.refreshSessionService = refreshSessionService;
    }

    /** Compatibility constructor for focused realtime tests. */
    public RealtimeAccountAccessInterceptor(
            JwtService jwtService,
            AccountAccessGuard accountAccessGuard,
            ProjectOwnershipGuard ownershipGuard,
            FeedbackReportRepository feedbackReportRepository) {
        this(jwtService, accountAccessGuard, ownershipGuard, feedbackReportRepository, null);
    }

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
        if (projectId != null) {
            return handleProjectMessage(message, command, sessionId, projectId);
        }

        UUID reportId = reportId(accessor.getDestination());
        if (reportId != null) {
            return handleReportMessage(message, command, sessionId, reportId);
        }

        if (isAdminTopic(accessor.getDestination())) {
            return handleAdminMessage(message, command, sessionId);
        }

        if (command == StompCommand.SEND) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        return message;
    }

    private Message<?> handleProjectMessage(
            Message<?> message,
            StompCommand command,
            String sessionId,
            String projectId
    ) {
        UUID userId = sessionId == null ? null : userIdsBySession.get(sessionId);
        if (!isAuthSessionActive(sessionId, userId) || !accountAccessGuard.canAccessRealtime(userId)) {
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

    private Message<?> handleReportMessage(
            Message<?> message,
            StompCommand command,
            String sessionId,
            UUID reportId
    ) {
        UUID userId = sessionId == null ? null : userIdsBySession.get(sessionId);
        if (!isAuthSessionActive(sessionId, userId) || !accountAccessGuard.canAccessSupportRealtime(userId)) {
            return rejectOrSuppress(command);
        }
        if (command == StompCommand.SEND) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        if (command == StompCommand.SUBSCRIBE) {
            assertCanSubscribeReport(sessionId, userId, reportId);
            reportIdsBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet())
                    .add(reportId);
            return message;
        }
        return isAuthorizedSessionReport(sessionId, reportId) ? message : null;
    }

    private Message<?> handleAdminMessage(
            Message<?> message,
            StompCommand command,
            String sessionId
    ) {
        UUID userId = sessionId == null ? null : userIdsBySession.get(sessionId);
        if (!isAuthSessionActive(sessionId, userId)
                || !Boolean.TRUE.equals(adminSessionsBySession.get(sessionId))) {
            return rejectOrSuppress(command);
        }
        if (command == StompCommand.SEND) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        return message;
    }

    private boolean isAdminTopic(String destination) {
        return destination != null && destination.startsWith(ADMIN_TOPIC_PREFIX);
    }

    private void authenticateSession(String sessionId, StompHeaderAccessor accessor) {
        if (sessionId == null) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE);
        }
        try {
            AuthenticatedUser user = authenticatedUser(accessor);
            if (!isAccessSessionActive(user)) {
                throw new AccessDeniedException(RESTRICTED_MESSAGE);
            }
            if (!accountAccessGuard.canAccessSupportRealtime(user.id())) {
                throw new AccessDeniedException(RESTRICTED_MESSAGE);
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
            userIdsBySession.put(sessionId, user.id());
            if (user.sessionId() != null) {
                authSessionIdsBySession.put(sessionId, user.sessionId());
            }
            adminSessionsBySession.put(sessionId, user.role() == Role.ADMIN);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AccessDeniedException(RESTRICTED_MESSAGE, ex);
        }
    }

    private AuthenticatedUser authenticatedUser(StompHeaderAccessor accessor) {
        String token = bearerToken(accessor);
        if (token != null) {
            return jwtService.parse(token);
        }
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AccessDeniedException(RESTRICTED_MESSAGE);
    }

    private String bearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
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

    private boolean isAuthorizedSessionReport(String sessionId, UUID reportId) {
        return sessionId != null
                && reportIdsBySession.getOrDefault(sessionId, Set.of()).contains(reportId);
    }

    private void assertCanSubscribeReport(String sessionId, UUID userId, UUID reportId) {
        if (Boolean.TRUE.equals(adminSessionsBySession.get(sessionId))
                && feedbackReportRepository.existsById(reportId)) {
            return;
        }
        if (feedbackReportRepository.findByIdAndUserId(reportId, userId).isPresent()) {
            return;
        }
        throw new AccessDeniedException(RESTRICTED_MESSAGE);
    }

    private void removeSession(String sessionId) {
        if (sessionId != null) {
            userIdsBySession.remove(sessionId);
            authSessionIdsBySession.remove(sessionId);
            adminSessionsBySession.remove(sessionId);
            projectIdsBySession.remove(sessionId);
            reportIdsBySession.remove(sessionId);
        }
    }

    private boolean isAccessSessionActive(AuthenticatedUser user) {
        return refreshSessionService == null
                || refreshSessionService.isAccessSessionActive(user.sessionId(), user.id());
    }

    private boolean isAuthSessionActive(String stompSessionId, UUID userId) {
        if (refreshSessionService == null || stompSessionId == null || userId == null) {
            return true;
        }
        UUID authSessionId = authSessionIdsBySession.get(stompSessionId);
        return authSessionId == null || refreshSessionService.isAccessSessionActive(authSessionId, userId);
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

    private UUID reportId(String destination) {
        if (destination == null || !destination.startsWith(REPORT_TOPIC_PREFIX)) {
            return null;
        }
        String remainder = destination.substring(REPORT_TOPIC_PREFIX.length());
        if (remainder.contains("/")) {
            return null;
        }
        try {
            return UUID.fromString(remainder);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
