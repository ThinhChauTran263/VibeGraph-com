package com.vibegraph.diagram.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vibegraph.diagram.service.impl.MermaidGeneratorServiceImpl;

@DisplayName("MermaidGeneratorService")
class MermaidGeneratorServiceTest {

    private MermaidGeneratorService generator;

    @BeforeEach
    void setUp() {
        generator = new MermaidGeneratorServiceImpl();
    }

    @Test
    @DisplayName("sanitizeId replaces non-identifier characters with underscores")
    void sanitizeIdReplacesSpecials() {
        assertThat(generator.sanitizeId("GET /api/users/{id}")).isEqualTo("GET_api_users_id");
    }

    @Test
    @DisplayName("sanitizeId collapses runs and trims edge underscores")
    void sanitizeIdCollapsesAndTrims() {
        assertThat(generator.sanitizeId("  --foo//bar--  ")).isEqualTo("foo_bar");
    }

    @Test
    @DisplayName("sanitizeId prefixes ids that would otherwise start with a digit")
    void sanitizeIdAvoidsLeadingDigit() {
        assertThat(generator.sanitizeId("123route")).isEqualTo("n_123route");
    }

    @Test
    @DisplayName("sanitizeId falls back for null/blank/symbol-only input")
    void sanitizeIdFallback() {
        assertThat(generator.sanitizeId(null)).isEqualTo("n");
        assertThat(generator.sanitizeId("   ")).isEqualTo("n");
        assertThat(generator.sanitizeId("///")).isEqualTo("n");
    }

    @Test
    @DisplayName("escapeLabel neutralises double quotes")
    void escapeLabelQuotes() {
        assertThat(generator.escapeLabel("say \"hi\"")).isEqualTo("say #quot;hi#quot;");
    }

    @Test
    @DisplayName("escapeLabel collapses line breaks and control characters to spaces")
    void escapeLabelLineBreaks() {
        assertThat(generator.escapeLabel("line1\nline2\tend")).isEqualTo("line1 line2 end");
    }

    @Test
    @DisplayName("escapeLabel returns empty string for null")
    void escapeLabelNull() {
        assertThat(generator.escapeLabel(null)).isEmpty();
    }
}
