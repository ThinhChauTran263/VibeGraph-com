package com.vibegraph.auth.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.domain.UserCreditBalance;

import jakarta.persistence.LockModeType;

@Repository
public interface UserCreditBalanceRepository extends JpaRepository<UserCreditBalance, UUID> {

    Optional<UserCreditBalance> findByUserIdAndPeriodStartAndPeriodEnd(
            UUID userId, LocalDate periodStart, LocalDate periodEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM UserCreditBalance b
            WHERE b.userId = :userId
              AND b.periodStart <= :date
              AND b.periodEnd >= :date
            ORDER BY b.createdAt ASC, b.id ASC
            """)
    List<UserCreditBalance> findActiveBalancesForUpdate(
            @Param("userId") UUID userId,
            @Param("date") LocalDate date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_credit_balances
            SET credits_used = credits_used + :amount
            WHERE id = :balanceId
              AND credits_used <= 2147483647 - :amount
              AND (
                  credits_limit_snapshot::bigint
                  + credits_adjustment::bigint
                  - credits_used::bigint
              ) >= :amount
            """, nativeQuery = true)
    int debitIfSufficient(
            @Param("balanceId") UUID balanceId,
            @Param("amount") int amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_credit_balances
            SET credits_adjustment = credits_adjustment + :delta
            WHERE id = :balanceId
              AND (credits_adjustment::bigint + CAST(:delta AS bigint))
                  BETWEEN -2147483648 AND 2147483647
            """, nativeQuery = true)
    int adjustCredits(
            @Param("balanceId") UUID balanceId,
            @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE user_credit_balances
            SET credits_limit_snapshot = :limit
            WHERE id = :balanceId
              AND :limit >= 0
            """, nativeQuery = true)
    int updateCreditsLimitSnapshot(
            @Param("balanceId") UUID balanceId,
            @Param("limit") int limit);
}
