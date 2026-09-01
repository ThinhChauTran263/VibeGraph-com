package com.vibegraph.auth.domain.entity;

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

/**
 * One size tier of the tiered import pricing model. An operation (e.g.
 * {@code IMPORT_ARCHIVE}) owns an ordered set of tiers; billing picks the
 * first tier whose {@code maxFiles} covers the imported {@code .java} file
 * count. A {@code null} {@code maxFiles} marks the unlimited top tier.
 */
@Entity
@Table(name = "import_pricing_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportPricingTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "operation_code", nullable = false)
    private String operationCode;

    @Column(name = "tier_code", nullable = false)
    private String tierCode;

    /** Inclusive upper file bound; null means unlimited (top tier). */
    @Column(name = "max_files")
    private Integer maxFiles;

    @Column(name = "credits", nullable = false)
    private int credits;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
