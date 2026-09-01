package com.vibegraph.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.vibegraph.auth.domain.entity.CreditLedger;
import com.vibegraph.auth.repository.projection.AdminSeriesRow;

@Repository
public interface CreditLedgerRepository extends JpaRepository<CreditLedger, UUID> {

    List<CreditLedger> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<CreditLedger> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS label,
                   sum(abs(credits_delta)) AS value,
                   'day' AS period
            FROM credit_ledger
            WHERE created_at IS NOT NULL
              AND credits_delta < 0
            GROUP BY date_trunc('day', created_at)
            ORDER BY date_trunc('day', created_at)
            """, nativeQuery = true)
    List<AdminSeriesRow> sumConsumptionByDay();

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS label,
                   sum(abs(credits_delta)) AS value,
                   'month' AS period
            FROM credit_ledger
            WHERE created_at IS NOT NULL
              AND credits_delta < 0
            GROUP BY date_trunc('month', created_at)
            ORDER BY date_trunc('month', created_at)
            """, nativeQuery = true)
    List<AdminSeriesRow> sumConsumptionByMonth();

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT concat(extract(year from created_at)::int, '-Q', extract(quarter from created_at)::int) AS label,
                   sum(abs(credits_delta)) AS value,
                   'quarter' AS period
            FROM credit_ledger
            WHERE created_at IS NOT NULL
              AND credits_delta < 0
            GROUP BY extract(year from created_at), extract(quarter from created_at)
            ORDER BY extract(year from created_at), extract(quarter from created_at)
            """, nativeQuery = true)
    List<AdminSeriesRow> sumConsumptionByQuarter();

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT extract(year from created_at)::int::text AS label,
                   sum(abs(credits_delta)) AS value,
                   'year' AS period
            FROM credit_ledger
            WHERE created_at IS NOT NULL
              AND credits_delta < 0
            GROUP BY extract(year from created_at)
            ORDER BY extract(year from created_at)
            """, nativeQuery = true)
    List<AdminSeriesRow> sumConsumptionByYear();
}
