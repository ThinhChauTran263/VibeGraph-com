package com.vibegraph.auth.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.domain.UserCreditBalance;

@Repository
public interface UserCreditBalanceRepository extends JpaRepository<UserCreditBalance, UUID> {

    @Query("SELECT b FROM UserCreditBalance b WHERE b.userId = :userId AND b.periodStart <= :date AND b.periodEnd >= :date")
    Optional<UserCreditBalance> findActiveBalance(@Param("userId") UUID userId, @Param("date") LocalDate date);

    Optional<UserCreditBalance> findFirstByUserIdOrderByPeriodEndDesc(UUID userId);
}
