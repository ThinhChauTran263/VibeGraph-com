package com.vibegraph.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.vibegraph.auth.config.ApiKeyEncryptionProperties;

/**
 * AES-256-GCM protector for revealable API-key credentials.
 *
 * <p>New ciphertext is prefixed with {@code v1:} and is always encrypted with the current key.
 * During key rotation, versioned ciphertext can be read with the previous key as well. Legacy
 * unversioned ciphertext is read only with the explicitly configured legacy secret, which is
 * derived using the historical SHA-256 scheme.
 */
@Component
public class ApiKeySecretProtector {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String VERSION_PREFIX = "v1:";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE;

    private final SecretKeySpec currentKey;
    private final SecretKeySpec previousKey;
    private final SecretKeySpec legacyKey;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a protector and validates all configured key material before serving requests.
     *
     * @param properties encryption key configuration
     * @throws IllegalStateException when the current or previous key is invalid
     */
    public ApiKeySecretProtector(ApiKeyEncryptionProperties properties) {
        if (properties == null) {
            throw new IllegalStateException("API key encryption properties must be configured");
        }
        this.currentKey = parseRequiredKey(properties.getCurrentKey(), "current");
        this.previousKey = parseOptionalKey(properties.getPreviousKey(), "previous");
        if (previousKey != null && sameKey(currentKey, previousKey)) {
            throw new IllegalStateException(
                    "API key encryption previous key must differ from current key");
        }
        this.legacyKey = deriveLegacyKey(properties.getLegacySecret());
    }

    /**
     * Encrypts a plaintext credential using the current key.
     *
     * @param plainSecret credential to protect
     * @return versioned Base64 ciphertext
     * @throws IllegalArgumentException when the credential is null or blank
     * @throws IllegalStateException when the JVM cannot provide AES-GCM
     */
    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            throw new IllegalArgumentException("API key secret must not be blank");
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(join(iv, sealed));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to protect the API key secret", ex);
        }
    }

    /**
     * Decrypts versioned or legacy ciphertext without changing the stored value.
     *
     * @param cipherText versioned or legacy Base64 ciphertext
     * @return decrypted credential
     * @throws IllegalStateException when the ciphertext is malformed or cannot be authenticated
     */
    public String decrypt(String cipherText) {
        try {
            if (cipherText == null || cipherText.isBlank()) {
                throw new GeneralSecurityException("blank ciphertext");
            }
            if (cipherText.startsWith(VERSION_PREFIX)) {
                byte[] payload = decodePayload(cipherText.substring(VERSION_PREFIX.length()));
                return decryptWithRotationKeys(payload);
            }
            if (cipherText.indexOf(':') >= 0) {
                throw new GeneralSecurityException("unsupported ciphertext version");
            }
            if (legacyKey == null) {
                throw new GeneralSecurityException("legacy key is unavailable");
            }
            return decryptPayload(decodePayload(cipherText), legacyKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to reveal the API key secret", ex);
        }
    }

    private String decryptWithRotationKeys(byte[] payload) throws GeneralSecurityException {
        GeneralSecurityException currentFailure;
        try {
            return decryptPayload(payload, currentKey);
        } catch (GeneralSecurityException ex) {
            currentFailure = ex;
        }
        if (previousKey != null) {
            try {
                return decryptPayload(payload, previousKey);
            } catch (GeneralSecurityException ex) {
                currentFailure.addSuppressed(ex);
            }
        }
        throw currentFailure;
    }

    private static String decryptPayload(byte[] payload, SecretKeySpec key)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(GCM_TAG_BITS, payload, 0, GCM_IV_BYTES));
        byte[] plain = cipher.doFinal(payload, GCM_IV_BYTES, payload.length - GCM_IV_BYTES);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] decodePayload(String encoded) throws GeneralSecurityException {
        final byte[] payload;
        try {
            payload = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new GeneralSecurityException("invalid Base64 ciphertext", ex);
        }
        if (payload.length < GCM_IV_BYTES + GCM_TAG_BYTES) {
            throw new GeneralSecurityException("ciphertext is too short");
        }
        return payload;
    }

    private static SecretKeySpec parseRequiredKey(String encoded, String label) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "API key encryption " + label + " key must be configured");
        }
        return parseKey(encoded, label);
    }

    private static SecretKeySpec parseOptionalKey(String encoded, String label) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        return parseKey(encoded, label);
    }

    private static SecretKeySpec parseKey(String encoded, String label) {
        final byte[] material;
        try {
            material = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "API key encryption " + label + " key is not valid base64", ex);
        }
        if (material.length != AES_KEY_BYTES) {
            throw new IllegalStateException(
                    "API key encryption " + label + " key must decode to 32 bytes");
        }
        return new SecretKeySpec(material, "AES");
    }

    private static SecretKeySpec deriveLegacyKey(String legacySecret) {
        if (legacySecret == null || legacySecret.isBlank()) {
            return null;
        }
        try {
            byte[] material = MessageDigest.getInstance("SHA-256")
                    .digest(legacySecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(material, "AES");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable in this JVM", ex);
        }
    }

    private static boolean sameKey(SecretKeySpec left, SecretKeySpec right) {
        return MessageDigest.isEqual(left.getEncoded(), right.getEncoded());
    }

    private static byte[] join(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
