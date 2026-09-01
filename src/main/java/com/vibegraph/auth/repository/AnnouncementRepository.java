package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.vibegraph.auth.domain.entity.Announcement;

public interface AnnouncementRepository {

    List<Announcement> findAll();

    Optional<Announcement> findById(UUID id);

    Announcement save(Announcement announcement);

    void deleteById(UUID id);

    int deleteExpiredBefore(Instant cutoff);
}
