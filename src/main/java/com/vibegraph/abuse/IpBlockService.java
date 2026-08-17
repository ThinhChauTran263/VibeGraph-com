package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.service.AuditService;

@Service
public class IpBlockService {

    /**
     * B-M9: the IP-block check runs on every request via {@code IpBlockFilter}; caching the
     * lookup for a short TTL removes one Postgres round-trip per request. Admin mutations
     * evict the affected IP, so block/unblock from the admin UI applies immediately; the TTL
     * only bounds staleness for out-of-band DB changes.
     */
    static final Duration ACTIVE_LOOKUP_TTL = Duration.ofSeconds(45);

    private final IpBlockRepository repository;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final AuditService auditService;
    // Caches Optional so "no active block" (the overwhelmingly common case) is cached too.
    private final Cache<String, Optional<IpBlock>> activeBlockCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(ACTIVE_LOOKUP_TTL)
            .build();

    public IpBlockService(IpBlockRepository repository, CurrentUser currentUser, Clock clock,
            AuditService auditService) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Optional<IpBlock> findActive(String ipAddress) {
        String canonical = ClientAddressResolver.canonicalize(ipAddress);
        return activeBlockCache.get(canonical, ip -> repository.findActive(ip, clock.instant()));
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
        activeBlockCache.invalidate(canonical); // a fresh block must bite immediately
        audit("IP_BLOCK", canonical);
        return saved;
    }

    @Transactional
    public IpBlock update(UUID id, String ipAddress, String safeReason, Instant expiresAt, boolean active) {
        IpBlock block = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("IP block not found"));
        String previousIp = block.getIpAddress();
        block.setIpAddress(exactIp(ipAddress));
        block.setSafeReason(safeReason.trim());
        block.setExpiresAt(expiresAt);
        block.setActive(active);
        IpBlock saved = repository.save(block);
        // Unblocking (or moving the block) must be visible without waiting for the TTL.
        activeBlockCache.invalidate(previousIp);
        activeBlockCache.invalidate(saved.getIpAddress());
        audit(active ? "IP_BLOCK" : "IP_UNBLOCK", block.getIpAddress());
        return saved;
    }

    @Transactional
    public void remove(UUID id) {
        IpBlock block = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("IP block not found"));
        repository.delete(block);
        activeBlockCache.invalidate(block.getIpAddress());
        audit("IP_UNBLOCK", block.getIpAddress());
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
        auditService.recordCurrentUser(action, null, "IP_ADDRESS", target, java.util.Map.of());
    }
}
