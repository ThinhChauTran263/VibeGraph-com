package com.vibegraph.auth.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.repository.FeatureFlagRepository;
import com.vibegraph.common.exception.FeatureDisabledException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeatureGateService {

    public static final String REGISTRATION = "registration";
    public static final String API_KEYS_CREATE_GLOBAL = "api_keys.create.global";
    public static final String CLI_PUSH = "cli.push";
    public static final String IMPORT_LOCAL = "import.local";
    public static final String IMPORT_ARCHIVE = "import.archive";
    public static final String IMPORT_GITHUB = "import.github";
    public static final String PROJECT_ANALYZE = "project.analyze";
    public static final String MCP_ENABLED = "mcp.enabled";
    public static final String USECASE_GENERATE = "usecase.generate";

    public static final String MCP_TOOL_PREFIX = "mcp.tool.";

    private static final String[] GLOBAL_KEYS = {
        REGISTRATION,
        API_KEYS_CREATE_GLOBAL,
        CLI_PUSH,
        IMPORT_LOCAL,
        IMPORT_ARCHIVE,
        IMPORT_GITHUB,
        PROJECT_ANALYZE,
        MCP_ENABLED,
        USECASE_GENERATE
    };

    public static String normalizeMcpToolName(String toolName) {
        return toolName == null
                ? ""
                : toolName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    public static boolean isCanonicalGlobalKey(String key) {
        for (String globalKey : GLOBAL_KEYS) {
            if (globalKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCanonicalMcpToolKey(String key) {
        if (key == null || !key.startsWith(MCP_TOOL_PREFIX)) {
            return false;
        }
        String toolName = key.substring(MCP_TOOL_PREFIX.length());
        return !toolName.isBlank() && toolName.equals(normalizeMcpToolName(toolName));
    }

    private final FeatureFlagRepository featureFlagRepository;

    @Transactional(readOnly = true)
    public void assertEnabled(String key) {
        if (featureFlagRepository.existsByKeyAndEnabledFalse(key)) {
            throw new FeatureDisabledException(key);
        }
    }

    @Transactional(readOnly = true)
    public void assertMcpToolEnabled(String toolName) {
        assertEnabled(MCP_ENABLED);
        assertEnabled(MCP_TOOL_PREFIX + normalizeMcpToolName(toolName));
    }

}
