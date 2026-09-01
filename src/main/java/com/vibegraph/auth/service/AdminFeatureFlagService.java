package com.vibegraph.auth.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.entity.FeatureFlag;
import com.vibegraph.auth.dto.FeatureFlagRequest;
import com.vibegraph.auth.dto.FeatureFlagResponse;
import com.vibegraph.auth.repository.FeatureFlagRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminFeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> list() {
        return featureFlagRepository.findAll().stream()
                .map(FeatureFlagResponse::from)
                .toList();
    }

    @Transactional
    public FeatureFlagResponse create(FeatureFlagRequest request) {
        validateKeyAndScope(request.key(), request.scope());
        if (featureFlagRepository.existsByKey(request.key())) {
            throw new IllegalArgumentException("Feature flag key already exists");
        }
        FeatureFlagResponse response = FeatureFlagResponse.from(
                featureFlagRepository.save(toFlag(FeatureFlag.builder().build(), request)));
        auditChange(response);
        return response;
    }

    @Transactional
    public FeatureFlagResponse update(String key, FeatureFlagRequest request) {
        if (!key.equals(request.key())) {
            throw new IllegalArgumentException("Feature flag key cannot be changed");
        }
        validateKeyAndScope(request.key(), request.scope());
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + key));
        FeatureFlagResponse response = FeatureFlagResponse.from(featureFlagRepository.save(toFlag(flag, request)));
        auditChange(response);
        return response;
    }

    @Transactional
    public void delete(String key) {
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + key));
        featureFlagRepository.delete(flag);
        auditService.recordCurrentUser(
                "FEATURE_FLAG_CHANGE", null, "FEATURE_FLAG", key,
                java.util.Map.of("operation", "DELETE"));
    }

    private void validateKeyAndScope(String key, String scope) {
        if (FeatureGateService.isCanonicalGlobalKey(key)) {
            if (!"GLOBAL".equals(scope)) {
                throw new IllegalArgumentException("Global feature flags require GLOBAL scope");
            }
            return;
        }
        if (FeatureGateService.isCanonicalMcpToolKey(key)) {
            if (!"MCP_TOOL".equals(scope)) {
                throw new IllegalArgumentException("MCP tool feature flags require MCP_TOOL scope");
            }
            return;
        }
        throw new IllegalArgumentException("Unsupported feature flag key");
    }

    private FeatureFlag toFlag(FeatureFlag flag, FeatureFlagRequest request) {
        flag.setKey(request.key());
        flag.setScope(request.scope());
        flag.setDisplayName(request.displayName());
        flag.setEnabled(request.enabled());
        flag.setDescription(request.description());
        return flag;
    }

    private void auditChange(FeatureFlagResponse response) {
        auditService.recordCurrentUser(
                "FEATURE_FLAG_CHANGE", null, "FEATURE_FLAG", response.key(),
                java.util.Map.of("enabled", response.enabled(), "scope", response.scope()));
    }
}
