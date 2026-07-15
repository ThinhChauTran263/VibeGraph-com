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

import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.JwtService;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;

@ExtendWith(MockitoExtension.class)
@DisplayName("Realtime account access interceptor")
class RealtimeAccountAccessInterceptorTest {

    private static final String SESSION_ID = "session-1";
    private static final String PROJECT_ID = "p1";

    @Mock private JwtService jwtService;
    @Mock private AccountAccessGuard accountAccessGuard;
    @Mock private ProjectOwnershipGuard ownershipGuard;
    @Mock private MessageChannel channel;

    private UUID userId;
    private RealtimeAccountAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        interceptor = new RealtimeAccountAccessInterceptor(
                jwtService, accountAccessGuard, ownershipGuard);
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
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true, true, false);
        connectUser();
        interceptor.preSend(subscribeMessage(PROJECT_ID, "updates"), channel);

        assertThat(interceptor.preSend(outboundMessage(PROJECT_ID, "updates"), channel)).isNull();
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
    @DisplayName("a blocked account cannot connect")
    void preSend_blockedUser_rejectsConnect() {
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(userId, "user@test.local", Role.USER));
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage(), channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Account cannot access realtime project updates");
        verify(ownershipGuard, never()).assertOwner(PROJECT_ID, userId);
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
    @DisplayName("non-project messages are not filtered")
    void preSend_nonProjectMessage_isUnchanged() {
        Message<byte[]> message = outboundMessage("system", "announcement");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
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
        when(accountAccessGuard.canAccessRealtime(userId)).thenReturn(true);
        connectUser();
    }

    private void connectUser() {
        when(jwtService.parse("jwt-token"))
                .thenReturn(new AuthenticatedUser(userId, "user@test.local", Role.USER));
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

    private Message<byte[]> disconnectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(SESSION_ID);
        return message(accessor);
    }

    private Message<byte[]> outboundMessage(String projectId, String topic) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/topic/projects/" + projectId + "/" + topic);
        accessor.setMessageTypeIfNotSet(SimpMessageType.MESSAGE);
        return message(accessor);
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
