package com.vibegraph.auth.oauth;

import com.vibegraph.auth.domain.AuthProvider;

/** Provider profile fields that are safe to consume for local account linking. */
public record OAuthAccountProfile(
        AuthProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl) {
}
