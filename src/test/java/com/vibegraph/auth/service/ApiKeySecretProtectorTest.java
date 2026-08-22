package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.vibegraph.auth.config.ApiKeyEncryptionProperties;

class ApiKeySecretProtectorTest {

    private static final String LEGACY_SECRET = "legacy-jwt-secret-for-api-key-tests";
    private static final String API_SECRET = "vbg_test_api_key_secret_123456789";

    @Test
    void encrypt_currentKeyProducesVersionedCipherThatRoundTrips() {
        ApiKeySecretProtector protector = protector(keyMaterial(1), null, LEGACY_SECRET);

        String cipherText = protector.encrypt(API_SECRET);

        assertThat(cipherText).startsWith("v1:");
        assertThat(cipherText).doesNotContain(API_SECRET);
        assertThat(protector.decrypt(cipherText)).isEqualTo(API_SECRET);
    }

    @Test
    void encrypt_sameSecretUsesUniqueInitializationVectors() {
        ApiKeySecretProtector protector = protector(keyMaterial(11), null, LEGACY_SECRET);

        String first = protector.encrypt(API_SECRET);
        String second = protector.encrypt(API_SECRET);

        assertThat(first).isNotEqualTo(second);
        assertThat(protector.decrypt(first)).isEqualTo(API_SECRET);
        assertThat(protector.decrypt(second)).isEqualTo(API_SECRET);
    }

    @Test
    void decrypt_previousKeySupportsSafeKeyRotation() {
        String previousKey = keyMaterial(2);
        String cipherText = protector(previousKey, null, LEGACY_SECRET).encrypt(API_SECRET);
        ApiKeySecretProtector rotated = protector(keyMaterial(3), previousKey, LEGACY_SECRET);

        assertThat(rotated.decrypt(cipherText)).isEqualTo(API_SECRET);
    }

    @Test
    void decrypt_unversionedCipherFallsBackToLegacySecret() throws Exception {
        String legacyCipher = legacyCipher(LEGACY_SECRET, API_SECRET);
        ApiKeySecretProtector protector = protector(keyMaterial(4), null, LEGACY_SECRET);

        assertThat(protector.decrypt(legacyCipher)).isEqualTo(API_SECRET);
    }

    @Test
    void decrypt_unversionedCipherWithoutLegacySecretFailsWithoutGuessingJwtSecret()
            throws Exception {
        String legacyCipher = legacyCipher(LEGACY_SECRET, API_SECRET);
        ApiKeySecretProtector protector = protector(keyMaterial(5), null, "");

        assertThatThrownBy(() -> protector.decrypt(legacyCipher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to reveal the API key secret");
    }

    @Test
    void constructor_rejectsMissingCurrentKey() {
        assertThatThrownBy(() -> protector(null, null, LEGACY_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API key encryption current key must be configured");
    }

    @Test
    void constructor_rejectsMissingProperties() {
        assertThatThrownBy(() -> new ApiKeySecretProtector(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API key encryption properties must be configured");
    }

    @Test
    void constructor_rejectsNon256BitCurrentKey() {
        String invalidKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> protector(invalidKey, null, LEGACY_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API key encryption current key must decode to 32 bytes");
    }

    @Test
    void constructor_rejectsMalformedCurrentKey() {
        assertThatThrownBy(() -> protector("not-base64", null, LEGACY_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API key encryption current key is not valid base64");
    }

    @Test
    void constructor_rejectsMalformedPreviousKey() {
        assertThatThrownBy(() -> protector(keyMaterial(6), "not-base64", LEGACY_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API key encryption previous key is not valid base64");
    }

    @Test
    void constructor_rejectsDuplicateCurrentAndPreviousKey() {
        String key = keyMaterial(8);

        assertThatThrownBy(() -> protector(key, key, LEGACY_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API key encryption previous key must differ from current key");
    }

    @Test
    void encrypt_rejectsBlankSecret() {
        ApiKeySecretProtector protector = protector(keyMaterial(9), null, LEGACY_SECRET);

        assertThatThrownBy(() -> protector.encrypt("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("API key secret must not be blank");
    }

    @Test
    void decrypt_rejectsUnknownVersionAndMalformedPayload() {
        ApiKeySecretProtector protector = protector(keyMaterial(7), null, LEGACY_SECRET);

        assertThatThrownBy(() -> protector.decrypt("v2:unsupported"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to reveal the API key secret");
        assertThatThrownBy(() -> protector.decrypt(
                "v1:" + Base64.getEncoder().encodeToString(new byte[12])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to reveal the API key secret");
    }

    @Test
    void decrypt_rejectsTamperedAuthenticationTag() {
        ApiKeySecretProtector protector = protector(keyMaterial(10), null, LEGACY_SECRET);
        String cipherText = protector.encrypt(API_SECRET);
        byte[] payload = Base64.getDecoder().decode(cipherText.substring(3));
        payload[payload.length - 1] ^= 1;

        assertThatThrownBy(() -> protector.decrypt(
                "v1:" + Base64.getEncoder().encodeToString(payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to reveal the API key secret");
    }

    private static ApiKeySecretProtector protector(String currentKey, String previousKey,
            String legacySecret) {
        ApiKeyEncryptionProperties properties = new ApiKeyEncryptionProperties();
        properties.setCurrentKey(currentKey);
        properties.setPreviousKey(previousKey);
        properties.setLegacySecret(legacySecret);
        return new ApiKeySecretProtector(properties);
    }

    private static String keyMaterial(int seed) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (seed + index * 31);
        }
        return Base64.getEncoder().encodeToString(key);
    }

    private static String legacyCipher(String secret, String plainText) throws Exception {
        byte[] keyMaterial = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        byte[] iv = new byte[12];
        new SecureRandom(new byte[] {42}).nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyMaterial, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] sealed = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + sealed.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(sealed, 0, payload, iv.length, sealed.length);
        return Base64.getEncoder().encodeToString(payload);
    }
}
