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
@Table(name = "credit_pricing_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditPricingRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String operationCode; // e.g. "MCP_TOOL_CALL", "CLI_PUSH"

    private long baseCredits;
    private long perFileCredits;
    private long perMbCredits;
    private long per1kNodesCredits;
    private long minimumCredits;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
