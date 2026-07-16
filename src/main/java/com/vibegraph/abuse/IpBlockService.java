package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AuditService;

@Service
public class IpBlockService {

    private final IpBlockRepository repository;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final AuditService auditService;

    public IpBlockService(IpBlockRepository repository, CurrentUser currentUser, Clock clock) {
        this(repository, currentUser, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IpBlockService(IpBlockRepository repository, CurrentUser currentUser, Clock clock,
            AuditService auditService) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Optional<IpBlock> findActive(String ipAddress) {
        return repository.findActive(ClientAddressResolver.canonicalize(ipAddress), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<IpBlock> list(int limit) {
        return repository.findAllByOrderByActiveDescUpdatedAtDesc(PageRequest.of(0, bound(limit)));
    }

    @Transactional
    public IpBlock create(String ipAddress, String safeReason, Instant expiresAt) {
        String canonical = exactIp(ipAddress);
        if (repository.findActive(canonical, clock.instant()).isPresent()) {
            throw new IllegalArgumentException("An active block already exists for this IP");
        }
        IpBlock saved = repository.save(IpBlock.builder()
                .ipAddress(canonical)
                .safeReason(safeReason.trim())
                .expiresAt(expiresAt)
                .createdBy(currentUser.id())
                .active(true)
                .build());
        audit("IP_BLOCK", canonical);
        return saved;
    }

    @Transactional
    public IpBlock update(UUID id, String ipAddress, String safeReason, Instant expiresAt, boolean active) {
        IpBlock block = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("IP block not found"));
        block.setIpAddress(exactIp(ipAddress));
        block.setSafeReason(safeReason.trim());
        block.setExpiresAt(expiresAt);
        block.setActive(active);
        IpBlock saved = repository.save(block);
        audit(active ? "IP_BLOCK" : "IP_UNBLOCK", block.getIpAddress());
        return saved;
    }

    @Transactional
    public void remove(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("IP block not found");
        }
        repository.deleteById(id);
        audit("IP_UNBLOCK", id.toString());
    }

    private String exactIp(String value) {
        if (value == null || value.contains("/")) {
            throw new IllegalArgumentException("Only exact IP addresses are supported");
        }
        return ClientAddressResolver.canonicalize(value);
    }

    private int bound(int limit) {
        return Math.min(Math.max(limit, 1), 200);
    }

    private void audit(String action, String target) {
        if (auditService != null) {
            auditService.recordCurrentUser(action, null, "IP_ADDRESS", target, java.util.Map.of());
        }
    }
}
