package com.vibegraph.auth.domain.entity;

import java.math.BigDecimal;
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
@Table(name = "credit_pricing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditPricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "operation_code", nullable = false, unique = true)
    private String operationCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Builder.Default
    @Column(name = "base_credits", nullable = false)
    private BigDecimal baseCredits = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "per_file_credits", nullable = false)
    private BigDecimal perFileCredits = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "per_mb_credits", nullable = false)
    private BigDecimal perMbCredits = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "per_1k_nodes_credits", nullable = false)
    private BigDecimal per1kNodesCredits = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "minimum_credits", nullable = false)
    private int minimumCredits = 0;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
