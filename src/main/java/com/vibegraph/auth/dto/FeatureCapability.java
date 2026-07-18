package com.vibegraph.auth.dto;

/** Safe client-facing feature capability projection. */
public record FeatureCapability(boolean enabled, String reason) {

    public static FeatureCapability allow() {
        return new FeatureCapability(true, null);
    }

    public static FeatureCapability deny(String reason) {
        return new FeatureCapability(false, reason);
    }
}
