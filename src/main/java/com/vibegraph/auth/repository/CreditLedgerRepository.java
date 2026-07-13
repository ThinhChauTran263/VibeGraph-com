package com.vibegraph.auth.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vibegraph.auth.domain.CreditLedger;

public interface CreditLedgerRepository extends JpaRepository<CreditLedger, UUID> {
}
