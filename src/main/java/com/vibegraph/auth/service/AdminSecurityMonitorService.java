package com.vibegraph.auth.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.abuse.RequestAggregateResponse;
import com.vibegraph.abuse.RequestEvent;
import com.vibegraph.abuse.RequestEventRepository;
import com.vibegraph.abuse.RequestEventResponse;
import com.vibegraph.abuse.SuspiciousNetworkBreakdownResponse;
import com.vibegraph.abuse.SuspiciousNetworkResponse;
import com.vibegraph.auth.domain.User;
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

    @Transactional(readOnly = true)
    public List<SecurityEventResponse> recentEvents(int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        return securityEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, boundedLimit)).stream()
                .map(SecurityEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<RequestAggregateResponse> topUsers(int minutes, int limit) {
        return requestEventRepository.topUsers(since(minutes), PageRequest.of(0, bound(limit))).stream()
                .map(RequestAggregateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequestAggregateResponse> topIps(int minutes, int limit) {
        return requestEventRepository.topIps(since(minutes), PageRequest.of(0, bound(limit))).stream()
                .map(RequestAggregateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
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
                        Collectors.mapping(SuspiciousNetworkBreakdownResponse::from, Collectors.toList())));
        return networks.stream()
                .map(network -> SuspiciousNetworkResponse.from(network,
                        breakdownByIp.getOrDefault(network.getIpAddress(), List.of())))
                .toList();
    }

    private java.time.Instant since(int minutes) {
        return java.time.Instant.now().minus(Math.min(Math.max(minutes, 1), 1440),
                java.time.temporal.ChronoUnit.MINUTES);
    }

    private int bound(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }
}
