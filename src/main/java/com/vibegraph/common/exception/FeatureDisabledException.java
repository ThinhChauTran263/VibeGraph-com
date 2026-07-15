package com.vibegraph.common.exception;

public class FeatureDisabledException extends RuntimeException {

    public FeatureDisabledException(String featureKey) {
        super("Feature is currently disabled: " + featureKey);
    }

    public String getCode() {
        return "FEATURE_DISABLED";
    }
}
