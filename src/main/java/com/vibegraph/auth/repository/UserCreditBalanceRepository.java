package com.vibegraph.auth.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vibegraph.auth.domain.UserCreditBalance;

public interface UserCreditBalanceRepository extends JpaRepository<UserCreditBalance, UUID> {
    Optional<UserCreditBalance> findByUserIdAndPeriodMonth(UUID userId, String periodMonth);
}
