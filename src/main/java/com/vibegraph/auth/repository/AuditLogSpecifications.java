package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.vibegraph.auth.domain.entity.AuditLog;

import jakarta.persistence.criteria.Predicate;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> withFilters(
            String action,
            String outcome,
            UUID actorUserId,
            UUID targetUserId,
            Instant from,
            Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }
            if (outcome != null) {
                predicates.add(criteriaBuilder.equal(root.get("outcome"), outcome));
            }
            if (actorUserId != null) {
                predicates.add(criteriaBuilder.equal(root.get("actorUserId"), actorUserId));
            }
            if (targetUserId != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetUserId"), targetUserId));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
