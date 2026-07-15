package com.vibegraph.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.repository.FeatureFlagRepository;
import com.vibegraph.common.exception.FeatureDisabledException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeatureGateService {

    public static final String GLOBAL_CLI_PUSH = "global.cli_push";
    public static final String GLOBAL_MCP = "global.mcp";
    public static final String GLOBAL_API_KEYS = "global.api_keys";
    public static final String GLOBAL_REGISTRATION = "global.registration";
    public static final String GLOBAL_IMPORT_ARCHIVE = "global.import_archive";
    public static final String GLOBAL_IMPORT_GITHUB = "global.import_github";

    private static final String MCP_TOOL_PREFIX = "mcp.tool.";

    private final FeatureFlagRepository featureFlagRepository;

    @Transactional(readOnly = true)
    public void assertEnabled(String key) {
        if (featureFlagRepository.existsByKeyAndEnabledFalse(key)) {
            throw new FeatureDisabledException(key);
        }
    }

    @Transactional(readOnly = true)
    public void assertMcpToolEnabled(String toolName) {
        assertEnabled(GLOBAL_MCP);
        assertEnabled(MCP_TOOL_PREFIX + normalizeToolName(toolName));
    }

    private String normalizeToolName(String toolName) {
        return toolName == null
                ? ""
                : toolName.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }
}
