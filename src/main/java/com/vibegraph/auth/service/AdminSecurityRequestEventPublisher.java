package com.vibegraph.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.vibegraph.abuse.RequestEvent;
import com.vibegraph.abuse.RequestEventResponse;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Publishes sanitized request events after their database transaction commits. */
@Service
@RequiredArgsConstructor
public class AdminSecurityRequestEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityRequestEventPublisher.class);

    private final UserRepository userRepository;
    private final AdminSecurityRequestEventStream eventStream;

    public void publishAfterCommit(RequestEvent event) {
        Runnable publish = () -> {
            if (eventStream.hasSubscribers()) {
                eventStream.publish(toResponse(event));
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    private RequestEventResponse toResponse(RequestEvent event) {
        User user = null;
        if (event.getUserId() != null) {
            try {
                user = userRepository.findById(event.getUserId()).orElse(null);
            } catch (RuntimeException ex) {
                log.warn("Could not resolve user identity for request event {}: {}",
                        event.getId(), ex.getMessage());
            }
        }
        return RequestEventResponse.from(event, user);
    }
}
