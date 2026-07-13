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
@Table(name = "credit_ledger")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String operationCode; // e.g. CLI_PUSH, IMPORT_GITHUB

    @Column(nullable = false)
    private long amount; // Negative for deduct, Positive for refund/bonus

    @Column(name = "reference_id")
    private String referenceId; // e.g. Project ID

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
