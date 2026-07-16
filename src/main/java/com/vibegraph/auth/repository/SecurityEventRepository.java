package com.vibegraph.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.SecurityEvent;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    List<SecurityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
