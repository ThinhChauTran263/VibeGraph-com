package com.vibegraph.auth.cli;

/** Lifecycle states for a short-lived CLI browser authorization request. */
public enum CliDeviceAuthorizationStatus {
    PENDING,
    APPROVED,
    CONSUMED,
    EXPIRED
}
