package com.vibegraph.auth.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.FeatureFlag;
import com.vibegraph.auth.dto.FeatureFlagRequest;
import com.vibegraph.auth.dto.FeatureFlagResponse;
import com.vibegraph.auth.repository.FeatureFlagRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminFeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> list() {
        return featureFlagRepository.findAll().stream()
                .map(FeatureFlagResponse::from)
                .toList();
    }

    @Transactional
    public FeatureFlagResponse create(FeatureFlagRequest request) {
        if (featureFlagRepository.existsByKey(request.key())) {
            throw new IllegalArgumentException("Feature flag key already exists");
        }
        return FeatureFlagResponse.from(featureFlagRepository.save(toFlag(FeatureFlag.builder().build(), request)));
    }

    @Transactional
    public FeatureFlagResponse update(String key, FeatureFlagRequest request) {
        if (!key.equals(request.key())) {
            throw new IllegalArgumentException("Feature flag key cannot be changed");
        }
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + key));
        return FeatureFlagResponse.from(featureFlagRepository.save(toFlag(flag, request)));
    }

    @Transactional
    public void delete(String key) {
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + key));
        featureFlagRepository.delete(flag);
    }

    private FeatureFlag toFlag(FeatureFlag flag, FeatureFlagRequest request) {
        flag.setKey(request.key());
        flag.setScope(request.scope());
        flag.setDisplayName(request.displayName());
        flag.setEnabled(request.enabled());
        flag.setDescription(request.description());
        return flag;
    }
}
