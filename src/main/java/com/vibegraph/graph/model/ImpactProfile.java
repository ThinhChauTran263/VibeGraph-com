package com.vibegraph.graph.model;

import java.util.Locale;

public enum ImpactProfile {
    DEPENDENCY("dependency", "CALLS|IMPORTS|EXTENDS|IMPLEMENTS|INJECTS", true),
    STRUCTURAL("structural", "CONTAINS|DEFINES|HAS_METHOD|HAS_FIELD|HANDLES_ROUTE", false),
    TYPE_DATA_FLOW("type-data-flow", "TYPE_OF|PARAMETER_TYPE|RETURNS|READS|WRITES|CATCHES|STEP_IN_FLOW", false);

    private final String apiValue;
    private final String relationshipPattern;
    private final boolean directedToTarget;

    ImpactProfile(String apiValue, String relationshipPattern, boolean directedToTarget) {
        this.apiValue = apiValue;
        this.relationshipPattern = relationshipPattern;
        this.directedToTarget = directedToTarget;
    }

    public String apiValue() {
        return apiValue;
    }

    public String relationshipPattern() {
        return relationshipPattern;
    }

    public boolean directedToTarget() {
        return directedToTarget;
    }

    public static ImpactProfile fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return DEPENDENCY;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ImpactProfile profile : values()) {
            if (profile.apiValue.equals(normalized)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("profile must be one of dependency, structural, type-data-flow");
    }
}
