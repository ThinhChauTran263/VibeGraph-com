package com.vibegraph.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibegraph.auth.domain.entity.ImportPricingTier;

public interface ImportPricingTierRepository extends JpaRepository<ImportPricingTier, UUID> {

    List<ImportPricingTier> findByOperationCodeOrderBySortOrderAsc(String operationCode);

    void deleteByOperationCode(String operationCode);
}
