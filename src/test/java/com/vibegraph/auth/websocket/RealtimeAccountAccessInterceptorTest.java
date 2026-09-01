package com.vibegraph.auth.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.repository.FeedbackReportRepository;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.auth.service.RefreshSessionService;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;

@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime account access interceptor")
class RealtimeAccountAccessInterceptorTest {

    private static final String SESSION_ID = "session-1";
    private static final String PROJECT_ID = "p1";

    @Mock private JwtService jwtService;
    @Mock private AccountAccessGuard accountAccessGuard;
    @Mock private ProjectOwnershipGuard ownershipGuard;
    @Mock private FeedbackReportRepository feedbackReportRepository;
    @Mock private RefreshSessionService refreshSessionService;
    @Mock private MessageChannel channel;

    private static final UUID REPORT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private UUID userId;
    private RealtimeAccountAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        interceptor = new RealtimeAccountAccessInterceptor(
                jwtService, accountAccessGuard, ownershipGuard, feedbackReportRepository);
    }

    @Test
    @DisplayName("an authenticated owner can subscribe and receive project updates")
    void preSend_authenticatedOwner_allowsProjectUpdate() {
        connectActiveUser();
        interceptor.preSend(subscribeMessage(PROJECT_ID, "updates"), channel);
        Message<byte[]> outbound = outboundMessage(PROJECT_ID, "updates");

        assertThat(interceptor.preSend(outbound, channel)).isSameAs(outbound);
        verify(ownershipGuard).assertOwner(PROJECT_ID, userId);
    }

    @Test
    @DisplayName("a subscriber blocked after connecting receives no further project updates")
    void preSend_blockedExistingSubscriber_suppressesProjectUpdate() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true, false);
        connectUser();
        interceptor.preSend(subscribeMessage(PROJECT_ID, "updates"), channel);

        assertThat(interceptor.preSend(outboundMessage(PROJECT_ID, "updates"), channel)).isNull();
    }

    @Test
    @DisplayName("a deactivated subscriber receives no further project status events")
    void preSend_deactivatedExistingSubscriber_suppressesProjectStatus() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true, false);
        connectUser();
        interceptor.preSend(subscribeMessage(PROJECT_ID, "status"), channel);

        assertThat(interceptor.preSend(outboundMessage(PROJECT_ID, "status"), channel)).isNull();
    }

    @Test
    @DisplayName("a revoked auth session suppresses existing realtime delivery")
    void preSend_revokedAuthSession_suppressesProjectUpdate() {
        UUID authSessionId = UUID.randomUUID();
        RealtimeAccountAccessInterceptor sessionAwareInterceptor =
                new RealtimeAccountAccessInterceptor(
                        jwtService,
                        accountAccessGuard,
                        ownershipGuard,
                        feedbackReportRepository,
                        refreshSessionService);
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(
                        userId, "user@test.local", Role.USER, authSessionId));
        when(refreshSessionService.isAccessSessionActive(authSessionId, userId))
                .thenReturn(true, true, false);
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true);

        sessionAwareInterceptor.preSend(connectMessage(), channel);
        sessionAwareInterceptor.preSend(subscribeMessage(PROJECT_ID, "updates"), channel);

        assertThat(sessionAwareInterceptor.preSend(outboundMessage(PROJECT_ID, "updates"), channel))
                .isNull();
    }

    @Test
    @DisplayName("a blocked subscriber cannot send project messages after status changes")
    void preSend_blockedExistingSubscriber_rejectsProjectSend() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true, false);
        connectUser();
        interceptor.preSend(subscribeMessage(PROJECT_ID, "updates"), channel);

        assertThatThrownBy(() -> interceptor.preSend(sendMessage(PROJECT_ID, "updates"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a non-owner cannot subscribe to another user's project topic")
    void preSend_nonOwner_rejectsProjectSubscription() {
        connectActiveUser();
        org.mockito.Mockito.doThrow(new com.vibegraph.common.exception.ForbiddenException("Access denied"))
                .when(ownershipGuard).assertOwner(PROJECT_ID, userId);

        assertThatThrownBy(() -> interceptor.preSend(
                        subscribeMessage(PROJECT_ID, "status"), channel))
                .isInstanceOf(com.vibegraph.common.exception.ForbiddenException.class);
    }

    @Test
    @DisplayName("a blocked account can still connect for support-only report topics")
    void preSend_blockedUser_canConnectForSupportTopics() {
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(userId, "user@test.local", Role.USER));
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);

        assertThat(interceptor.preSend(connectMessage(), channel)).isNotNull();
        verify(ownershipGuard, never()).assertOwner(PROJECT_ID, userId);
    }

    @Test
    @DisplayName("browser websocket CONNECT can authenticate from the HttpOnly cookie handshake principal")
    void preSend_cookieHandshakePrincipal_allowsConnectWithoutBearerHeader() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        AuthenticatedUser principal = new AuthenticatedUser(userId, "cookie@test.local", Role.USER);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(SESSION_ID);
        accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));

        assertThat(interceptor.preSend(message(accessor), channel)).isNotNull();
    }

    @Test
    @DisplayName("report owners can subscribe and receive report updates")
    void preSend_reportOwner_allowsReportTopic() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(feedbackReportRepository.findByIdAndUserId(REPORT_ID, userId))
                .thenReturn(java.util.Optional.of(new com.vibegraph.auth.domain.entity.FeedbackReport()));

        connectAs(Role.USER);
        interceptor.preSend(subscribeReportMessage(REPORT_ID), channel);

        Message<byte[]> outbound = outboundReportMessage(REPORT_ID);
        assertThat(interceptor.preSend(outbound, channel)).isSameAs(outbound);
    }

    @Test
    @DisplayName("an admin-blocked owner keeps receiving support replies after session revocation")
    void preSend_blockedOwnerWithRevokedSession_allowsReportUpdate() {
        UUID authSessionId = UUID.randomUUID();
        RealtimeAccountAccessInterceptor sessionAwareInterceptor =
                new RealtimeAccountAccessInterceptor(
                        jwtService,
                        accountAccessGuard,
                        ownershipGuard,
                        feedbackReportRepository,
                        refreshSessionService);
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(
                        userId, "blocked@test.local", Role.USER, authSessionId));
        when(refreshSessionService.isAccessSessionActive(authSessionId, userId)).thenReturn(false);
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(false);
        when(feedbackReportRepository.findByIdAndUserId(REPORT_ID, userId))
                .thenReturn(java.util.Optional.of(new com.vibegraph.auth.domain.entity.FeedbackReport()));

        sessionAwareInterceptor.preSend(connectMessage(), channel);
        sessionAwareInterceptor.preSend(subscribeReportMessage(REPORT_ID), channel);

        Message<byte[]> outbound = outboundReportMessage(REPORT_ID);
        assertThat(sessionAwareInterceptor.preSend(outbound, channel)).isSameAs(outbound);
    }

    @Test
    @DisplayName("a normal revoked session cannot reconnect to support topics")
    void preSend_activeOwnerWithRevokedSession_rejectsConnect() {
        UUID authSessionId = UUID.randomUUID();
        RealtimeAccountAccessInterceptor sessionAwareInterceptor =
                new RealtimeAccountAccessInterceptor(
                        jwtService,
                        accountAccessGuard,
                        ownershipGuard,
                        feedbackReportRepository,
                        refreshSessionService);
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(
                        userId, "active@test.local", Role.USER, authSessionId));
        when(refreshSessionService.isAccessSessionActive(authSessionId, userId)).thenReturn(false);
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true);

        assertThatThrownBy(() -> sessionAwareInterceptor.preSend(connectMessage(), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("admins can subscribe to any report topic")
    void preSend_admin_allowsReportTopic() {
        UUID reportId = UUID.randomUUID();
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(feedbackReportRepository.existsById(reportId)).thenReturn(true);

        connectAs(Role.ADMIN);
        interceptor.preSend(subscribeReportMessage(reportId), channel);

        Message<byte[]> outbound = outboundReportMessage(reportId);
        assertThat(interceptor.preSend(outbound, channel)).isSameAs(outbound);
    }

    @Test
    @DisplayName("a deactivated account cannot connect")
    void preSend_deactivatedUser_rejectsConnect() {
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(userId, "user@test.local", Role.USER));
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a deactivated report subscriber receives no further support updates")
    void preSend_deactivatedExistingReportSubscriber_suppressesReportUpdate() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true, true, false);
        when(feedbackReportRepository.findByIdAndUserId(REPORT_ID, userId))
                .thenReturn(java.util.Optional.of(new com.vibegraph.auth.domain.entity.FeedbackReport()));
        connectAs(Role.USER);
        interceptor.preSend(subscribeReportMessage(REPORT_ID), channel);

        assertThat(interceptor.preSend(outboundReportMessage(REPORT_ID), channel)).isNull();
    }

    @Test
    @DisplayName("a missing bearer token cannot connect")
    void preSend_missingBearerToken_rejectsConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(SESSION_ID);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("protected outbound messages fail closed without an authorized subscription")
    void preSend_unknownSession_suppressesProjectUpdate() {
        assertThat(interceptor.preSend(outboundMessage(PROJECT_ID, "updates"), channel)).isNull();
    }

    @Test
    @DisplayName("non-project outbound messages are not filtered")
    void preSend_nonProjectOutboundMessage_isUnchanged() {
        Message<byte[]> message = outboundMessage("system", "announcement");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("unknown SEND destinations are denied by default")
    void preSend_unknownSendDestination_rejectsMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/app/unknown");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("admins can subscribe and receive online-user snapshots")
    void preSend_admin_allowsAdminTopic() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        connectAs(Role.ADMIN);
        interceptor.preSend(subscribeAdminMessage(), channel);

        Message<byte[]> outbound = outboundAdminMessage();

        assertThat(interceptor.preSend(outbound, channel)).isSameAs(outbound);
    }

    @Test
    @DisplayName("non-admin users cannot subscribe to admin topics")
    void preSend_nonAdmin_rejectsAdminTopicSubscription() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        connectAs(Role.USER);

        assertThatThrownBy(() -> interceptor.preSend(subscribeAdminMessage(), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("admin topic deliveries fail closed without an authorized session")
    void preSend_unknownSession_suppressesAdminTopicUpdate() {
        assertThat(interceptor.preSend(outboundAdminMessage(), channel)).isNull();
    }

    @Test
    @DisplayName("disconnect removes the stored session authorization")
    void preSend_disconnect_removesSessionAuthorization() {
        connectActiveUser();
        interceptor.preSend(subscribeMessage(PROJECT_ID, "status"), channel);
        interceptor.preSend(disconnectMessage(), channel);

        assertThat(interceptor.preSend(outboundMessage(PROJECT_ID, "status"), channel)).isNull();
    }

    private void connectActiveUser() {
        when(accountAccessGuard.canAccessSupportRealtime(userId)).thenReturn(true);
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true);
        connectUser();
    }

    private void connectUser() {
        connectAs(Role.USER);
    }

    private void connectAs(Role role) {
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(userId, role.name().toLowerCase() + "@test.local", role));
        interceptor.preSend(connectMessage(), channel);
    }

    private Message<byte[]> connectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(SESSION_ID);
        accessor.addNativeHeader("Authorization", "Bearer jwt-token");
        return message(accessor);
    }

    private Message<byte[]> subscribeMessage(String projectId, String topic) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/projects/" + projectId + "/" + topic);
        return message(accessor);
    }

    private Message<byte[]> subscribeReportMessage(UUID reportId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/reports/" + reportId);
        return message(accessor);
    }

    private Message<byte[]> subscribeAdminMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/admin/online-users");
        return message(accessor);
    }

    private Message<byte[]> outboundAdminMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/admin/online-users");
        accessor.setMessageTypeIfNotSet(SimpMessageType.MESSAGE);
        return message(accessor);
    }

    private Message<byte[]> disconnectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(SESSION_ID);
        return message(accessor);
    }

    private Message<byte[]> sendMessage(String projectId, String topic) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/projects/" + projectId + "/" + topic);
        return message(accessor);
    }

    private Message<byte[]> outboundMessage(String projectId, String topic) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/projects/" + projectId + "/" + topic);
        accessor.setMessageTypeIfNotSet(SimpMessageType.MESSAGE);
        return message(accessor);
    }

    private Message<byte[]> outboundReportMessage(UUID reportId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/reports/" + reportId);
        accessor.setMessageTypeIfNotSet(SimpMessageType.MESSAGE);
        return message(accessor);
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
