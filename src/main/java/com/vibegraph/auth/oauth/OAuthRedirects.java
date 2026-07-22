package com.vibegraph.auth.oauth;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class OAuthRedirects {

    private final OAuthRedirectProperties properties;

    String successUrl() {
        return build(properties.getSuccessPath(), null);
    }

    String successUrlForRole(String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return build("/admin", null);
        }
        if ("USER".equalsIgnoreCase(role)) {
            return build("/dashboard", null);
        }
        return successUrl();
    }

    String loginErrorUrl(String errorCode) {
        return build(properties.getLoginPath(), errorCode);
    }

    private String build(String path, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getFrontendUrl())
                .replacePath(path)
                .replaceQuery(null);
        if (errorCode != null && !errorCode.isBlank()) {
            builder.queryParam("error", errorCode);
        }
        return builder.build().toUriString();
    }
}
