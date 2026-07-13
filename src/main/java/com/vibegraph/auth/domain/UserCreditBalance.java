package com.vibegraph.auth.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_credit_balances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserCreditBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // YYYY-MM to track monthly cycle
    @Column(name = "period_month", nullable = false)
    private String periodMonth;

    @Column(name = "plan_snapshot_code", nullable = false)
    private String planSnapshotCode;

    @Column(name = "allocated_credits", nullable = false)
    private long allocatedCredits;

    @Builder.Default
    @Column(name = "used_credits", nullable = false)
    private long usedCredits = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public long getRemainingCredits() {
        return Math.max(0L, allocatedCredits - usedCredits);
    }
}
