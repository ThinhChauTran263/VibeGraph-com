package com.vibegraph.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds the key material used to protect revealable API-key credentials.
 *
 * <p>The current and previous values are standard Base64 encodings of exactly 32 random bytes.
 * The legacy secret is only used to read ciphertext written before versioned encryption was
 * introduced; it is never used for new ciphertext.
 */
@ConfigurationProperties(prefix = "vibegraph.auth.api-key-encryption")
@Getter
@Setter
public class ApiKeyEncryptionProperties {

    /** Base64-encoded 256-bit key used for all newly encrypted credentials. */
    private String currentKey;

    /** Optional Base64-encoded 256-bit key retained during a rotation overlap window. */
    private String previousKey;

    /** Secret from the legacy JWT-derived scheme, retained only for unversioned ciphertext. */
    private String legacySecret;
}
