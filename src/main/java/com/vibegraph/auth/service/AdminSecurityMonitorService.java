package com.vibegraph.auth.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.abuse.RequestAggregateResponse;
import com.vibegraph.abuse.entity.RequestEvent;
import com.vibegraph.abuse.RequestEventRepository;
import com.vibegraph.abuse.RequestEventResponse;
import com.vibegraph.abuse.SuspiciousNetworkBreakdownResponse;
import com.vibegraph.abuse.SuspiciousNetworkResponse;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.dto.SecurityEventResponse;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminSecurityMonitorService {

    private final SecurityEventRepository securityEventRepository;
    private final RequestEventRepository requestEventRepository;
    private final UserRepository userRepository;

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<SecurityEventResponse> recentEvents(int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        return securityEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, boundedLimit)).stream()
                .map(SecurityEventResponse::from)
                .toList();
    }

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<RequestEventResponse> requestEvents(int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        List<RequestEvent> events = requestEventRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, boundedLimit));
        Map<UUID, User> usersById = userRepository.findAllById(events.stream()
                        .map(RequestEvent::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return events.stream()
                .map(event -> RequestEventResponse.from(event, usersById.get(event.getUserId())))
                .toList();
    }

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<RequestAggregateResponse> topUsers(int minutes, int limit) {
        var rows = requestEventRepository.topUsers(since(minutes), PageRequest.of(0, bound(limit)));
        Map<UUID, User> usersById = usersById(rows.stream()
                .map(com.vibegraph.abuse.RequestAggregateProjection::getUserId)
                .filter(Objects::nonNull)
                .toList());
        return rows.stream()
                .map(row -> RequestAggregateResponse.from(enrich(row, usersById.get(row.getUserId()))))
                .toList();
    }

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<RequestAggregateResponse> topIps(int minutes, int limit) {
        return requestEventRepository.topIps(since(minutes), PageRequest.of(0, bound(limit))).stream()
                .map(RequestAggregateResponse::from)
                .toList();
    }

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public List<SuspiciousNetworkResponse> suspiciousNetworks(int minutes, int limit) {
        java.time.Instant since = since(minutes);
        var networks = requestEventRepository.suspiciousNetworks(since, PageRequest.of(0, bound(limit)));
        if (networks.isEmpty()) {
            return List.of();
        }
        var breakdownByIp = requestEventRepository.networkBreakdowns(since, networks.stream()
                        .map(com.vibegraph.abuse.NetworkAggregateProjection::getIpAddress)
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(com.vibegraph.abuse.NetworkBreakdownProjection::getIpAddress,
                        Collectors.toList()));
        Map<UUID, User> usersById = usersById(breakdownByIp.values().stream()
                .flatMap(List::stream)
                .map(com.vibegraph.abuse.NetworkBreakdownProjection::getUserId)
                .filter(Objects::nonNull)
                .toList());
        return networks.stream()
                .map(network -> SuspiciousNetworkResponse.from(network,
                        breakdownByIp.getOrDefault(network.getIpAddress(), List.of()).stream()
                                .map(row -> SuspiciousNetworkBreakdownResponse.from(
                                        enrich(row, usersById.get(row.getUserId()))))
                                .toList()))
                .toList();
    }

    private Map<UUID, User> usersById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private com.vibegraph.abuse.RequestAggregateProjection enrich(
            com.vibegraph.abuse.RequestAggregateProjection row, User user) {
        return new EnrichedRequestAggregate(row, user);
    }

    private com.vibegraph.abuse.NetworkBreakdownProjection enrich(
            com.vibegraph.abuse.NetworkBreakdownProjection row, User user) {
        return new EnrichedNetworkBreakdown(row, user);
    }

    private java.time.Instant since(int minutes) {
        return java.time.Instant.now().minus(Math.min(Math.max(minutes, 1), 1440),
                java.time.temporal.ChronoUnit.MINUTES);
    }

    private int bound(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }

    private record EnrichedRequestAggregate(
            com.vibegraph.abuse.RequestAggregateProjection delegate,
            User user) implements com.vibegraph.abuse.RequestAggregateProjection {
        @Override public UUID getUserId() { return delegate.getUserId(); }
        @Override public String getUserDisplayName() { return user == null ? null : user.getDisplayName(); }
        @Override public String getUserEmail() { return user == null ? null : user.getEmail(); }
        @Override public String getIpAddress() { return delegate.getIpAddress(); }
        @Override public String getApiKeyRef() { return delegate.getApiKeyRef(); }
        @Override public java.time.Instant getMinuteBucket() { return delegate.getMinuteBucket(); }
        @Override public long getRequestCount() { return delegate.getRequestCount(); }
    }

    private record EnrichedNetworkBreakdown(
            com.vibegraph.abuse.NetworkBreakdownProjection delegate,
            User user) implements com.vibegraph.abuse.NetworkBreakdownProjection {
        @Override public String getIpAddress() { return delegate.getIpAddress(); }
        @Override public UUID getUserId() { return delegate.getUserId(); }
        @Override public String getUserDisplayName() { return user == null ? null : user.getDisplayName(); }
        @Override public String getUserEmail() { return user == null ? null : user.getEmail(); }
        @Override public String getApiKeyRef() { return delegate.getApiKeyRef(); }
        @Override public long getRequests() { return delegate.getRequests(); }
    }
}
