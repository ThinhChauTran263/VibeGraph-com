package com.vibegraph.auth.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.dto.SecurityEventResponse;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.abuse.RequestAggregateResponse;
import com.vibegraph.abuse.RequestEventRepository;
import com.vibegraph.abuse.RequestEventResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminSecurityMonitorService {

    private final SecurityEventRepository securityEventRepository;
    private final RequestEventRepository requestEventRepository;

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
        return requestEventRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, boundedLimit)).stream()
                .map(RequestEventResponse::from)
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

    private java.time.Instant since(int minutes) {
        return java.time.Instant.now().minus(Math.min(Math.max(minutes, 1), 1440),
                java.time.temporal.ChronoUnit.MINUTES);
    }

    private int bound(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }
}
