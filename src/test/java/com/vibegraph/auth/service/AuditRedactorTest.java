package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("AuditRedactor")
class AuditRedactorTest {

    private final AuditRedactor redactor = new AuditRedactor(new ObjectMapper());

    @Test
    @DisplayName("redacts secrets, passwords, JWTs, API key material, and source content")
    void redact_sensitiveFields_neverPersistsRawValues() {
        String result = redactor.redact(Map.of(
                "password", "Password123!",
                "authorization", "Bearer raw.jwt.value",
                "apiKeySecret", "vbg_super_secret",
                "privateSourceContent", "class Secret {}",
                "reason", "quota adjustment"));

        assertThat(result)
                .contains("quota adjustment")
                .contains("[REDACTED]")
                .doesNotContain("Password123!")
                .doesNotContain("raw.jwt.value")
                .doesNotContain("vbg_super_secret")
                .doesNotContain("class Secret");
    }

    @Test
    @DisplayName("redacts sensitive token patterns even when the field name is generic")
    void redact_genericString_masksBearerAndApiKeyPatterns() {
        String result = redactor.redact(Map.of(
                "message", "Authorization Bearer abc.def.ghi and key vbg_12345678901234567890"));

        assertThat(result)
                .doesNotContain("abc.def.ghi")
                .doesNotContain("vbg_12345678901234567890");
    }
}
