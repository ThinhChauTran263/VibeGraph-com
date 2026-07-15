package com.vibegraph.auth.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.dto.SecurityEventResponse;
import com.vibegraph.auth.repository.SecurityEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminSecurityMonitorService {

    private final SecurityEventRepository securityEventRepository;

    @Transactional(readOnly = true)
    public List<SecurityEventResponse> recentEvents(int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        return securityEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, boundedLimit)).stream()
                .map(SecurityEventResponse::from)
                .toList();
    }
}
