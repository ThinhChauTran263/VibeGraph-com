package com.vibegraph.abuse;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;

public interface RequestEventRepository {

    RequestEvent save(RequestEvent event);

    void saveAll(List<RequestEvent> events);

    List<RequestEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<RequestAggregateProjection> topUsers(Instant since, Pageable pageable);

    List<RequestAggregateProjection> topIps(Instant since, Pageable pageable);

    List<NetworkAggregateProjection> suspiciousNetworks(Instant since, Pageable pageable);

    List<NetworkBreakdownProjection> networkBreakdowns(Instant since, List<String> ipAddresses);

    int deleteOccurredBefore(Instant cutoff);
}
