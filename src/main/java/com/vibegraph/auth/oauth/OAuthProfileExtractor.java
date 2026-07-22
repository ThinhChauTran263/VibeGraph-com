package com.vibegraph.auth.oauth;

import java.util.Map;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.vibegraph.auth.domain.AuthProvider;

/** Extracts normalized, provider-specific profile fields from Spring Security principals. */
public final class OAuthProfileExtractor {

    private static final String UNSUPPORTED_PROVIDER = "oauth_unsupported_provider";
    private static final String MISSING_SUBJECT = "oauth_missing_subject";

    private OAuthProfileExtractor() {
    }

    public static OAuthAccountProfile from(OAuth2AuthenticationToken authentication) {
        String registrationId = authentication.getAuthorizedClientRegistrationId();
        AuthProvider provider = provider(registrationId);
        Map<String, Object> attributes = authentication.getPrincipal().getAttributes();
        return switch (provider) {
            case GOOGLE -> google(attributes);
            case GITHUB -> github(attributes);
        };
    }

    private static OAuthAccountProfile google(Map<String, Object> attributes) {
        String subject = requiredString(attributes, "sub");
        return new OAuthAccountProfile(
                AuthProvider.GOOGLE,
                subject,
                normalizedEmail(attributes.get("email")),
                asBoolean(attributes.get("email_verified")),
                asString(attributes.get("name")),
                asString(attributes.get("picture")));
    }

    private static OAuthAccountProfile github(Map<String, Object> attributes) {
        String subject = requiredString(attributes, "id");
        String displayName = asString(attributes.get("name"));
        if (displayName == null || displayName.isBlank()) {
            displayName = asString(attributes.get("login"));
        }
        return new OAuthAccountProfile(
                AuthProvider.GITHUB,
                subject,
                normalizedEmail(attributes.get("email")),
                asBoolean(attributes.get("email_verified")),
                displayName,
                asString(attributes.get("avatar_url")));
    }

    private static AuthProvider provider(String registrationId) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return AuthProvider.GOOGLE;
        }
        if ("github".equalsIgnoreCase(registrationId)) {
            return AuthProvider.GITHUB;
        }
        throw oauthError(UNSUPPORTED_PROVIDER);
    }

    private static String requiredString(Map<String, Object> attributes, String key) {
        String value = asString(attributes.get(key));
        if (value == null || value.isBlank()) {
            throw oauthError(MISSING_SUBJECT);
        }
        return value;
    }

    private static String normalizedEmail(Object value) {
        String email = asString(value);
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static OAuth2AuthenticationException oauthError(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }
}
