package com.vibegraph.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.vibegraph.auth.config.JwtProperties;

/**
 * AES-256-GCM protector for the revealable copy of API-key secrets.
 *
 * <p>Authentication still verifies against the one-way {@code key_hash}; this protector only
 * backs the owner-facing "reveal my key" flow. The AES key is derived (SHA-256) from the JWT
 * signing secret, which is already mandatory at startup, so no extra environment variable is
 * required. Output format: base64(iv || ciphertext+tag).
 */
@Component
public class ApiKeySecretProtector {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ApiKeySecretProtector(JwtProperties jwtProperties) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] material = digest.digest(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(material, "AES");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable in this JVM", ex);
        }
    }

    /** Encrypts a plaintext secret into the storable base64 cipher form. */
    public String encrypt(String plainSecret) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to protect the API key secret", ex);
        }
    }

    /** Decrypts a stored cipher back into the plaintext secret. */
    public String decrypt(String cipherText) {
        try {
            byte[] in = Base64.getDecoder().decode(cipherText);
            if (in.length <= GCM_IV_BYTES) {
                throw new IllegalStateException("Malformed API key cipher");
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, in, 0, GCM_IV_BYTES));
            byte[] plain = cipher.doFinal(in, GCM_IV_BYTES, in.length - GCM_IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to reveal the API key secret", ex);
        }
    }
}
