package com.vibegraph.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.domain.CreditLedger;

@Repository
public interface CreditLedgerRepository extends JpaRepository<CreditLedger, UUID> {

    List<CreditLedger> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
