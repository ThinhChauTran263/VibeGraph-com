package com.vibegraph.abuse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class RequestTelemetryNormalizerTest {

    @ParameterizedTest
    @CsvSource({
        "/api/projects/2f1b3c14-1f2a-4c1e-9e59-0b1d2f3a4b5c, /api/projects/{id}",
        "/api/projects/42/files, /api/projects/{id}/files",
        "/api/users/user@example.com, /api/users/{email}",
        "/api/tokens/deadbeefdeadbeefdead, /api/tokens/{token}",
        "/api/keys/vbg_ab12safecd34safe, /api/keys/{token}",
        "/api/projects/{id}, /api/projects/{id}",
        "'', /",
        "/, /"
    })
    @DisplayName("identifiers, e-mails and opaque tokens are masked")
    void normalizeRoute_sensitiveSegments_areMasked(String input, String expected) {
        assertThat(RequestTelemetryNormalizer.normalizeRoute(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("filesystem-like segments are masked")
    void normalizeRoute_filesystemSegments_areMasked() {
        assertThat(RequestTelemetryNormalizer.normalizeRoute("/import/C:\\Users\\alice\\secret"))
                .isEqualTo("/import/{path}");
        assertThat(RequestTelemetryNormalizer.normalizeRoute("/import/%5CUsers%5Calice"))
                .isEqualTo("/import/{path}");
        assertThat(RequestTelemetryNormalizer.normalizeRoute("/import/~alice"))
                .isEqualTo("/import/{path}");
    }

    @Test
    @DisplayName("query strings and fragments never reach the recorded route")
    void normalizeRoute_queryString_isDropped() {
        assertThat(RequestTelemetryNormalizer.normalizeRoute("/api/search?q=secret&token=abc"))
                .isEqualTo("/api/search");
        assertThat(RequestTelemetryNormalizer.normalizeRoute("/api/search#token=abc"))
                .isEqualTo("/api/search");
    }

    @Test
    @DisplayName("deep and long routes are bounded before they reach the database column")
    void normalizeRoute_longRoute_isTruncatedToColumnWidth() {
        String deep = "/a".repeat(40);
        String normalized = RequestTelemetryNormalizer.normalizeRoute(deep);
        assertThat(normalized).endsWith("/...");
        assertThat(normalized.length()).isLessThanOrEqualTo(
                RequestTelemetryNormalizer.MAX_ROUTE_LENGTH);

        String wide = "/" + "x".repeat(5_000);
        String normalizedWide = RequestTelemetryNormalizer.normalizeRoute(wide);
        assertThat(normalizedWide.length()).isLessThanOrEqualTo(
                RequestTelemetryNormalizer.MAX_ROUTE_LENGTH);
    }

    @Test
    @DisplayName("scalar fields are normalized and bounded")
    void normalizeScalarFields_areBounded() {
        assertThat(RequestTelemetryNormalizer.normalizeMethod("get")).isEqualTo("GET");
        assertThat(RequestTelemetryNormalizer.normalizeMethod(null)).isEqualTo("UNKNOWN");
        assertThat(RequestTelemetryNormalizer.normalizeMethod("P".repeat(50)).length())
                .isEqualTo(RequestTelemetryNormalizer.MAX_METHOD_LENGTH);

        assertThat(RequestTelemetryNormalizer.normalizeIpAddress(null)).isEqualTo("unknown");
        assertThat(RequestTelemetryNormalizer.normalizeIpAddress("1".repeat(500)).length())
                .isEqualTo(RequestTelemetryNormalizer.MAX_IP_LENGTH);

        assertThat(RequestTelemetryNormalizer.normalizeEventType(null)).isEqualTo("REQUEST");
        assertThat(RequestTelemetryNormalizer.normalizeEventType("E".repeat(200)).length())
                .isEqualTo(RequestTelemetryNormalizer.MAX_EVENT_TYPE_LENGTH);

        assertThat(RequestTelemetryNormalizer.normalizeApiKeyRef("  ")).isNull();
        assertThat(RequestTelemetryNormalizer.normalizeApiKeyRef("k".repeat(500)).length())
                .isEqualTo(RequestTelemetryNormalizer.MAX_API_KEY_REF_LENGTH);
    }

    @Test
    @DisplayName("control characters are stripped from recorded values")
    void normalize_controlCharacters_areStripped() {
        assertThat(RequestTelemetryNormalizer.normalizeRoute("/api/pro\njects")).isEqualTo("/api/projects");
        assertThat(RequestTelemetryNormalizer.normalizeIpAddress("203.0.113.5\r\n"))
                .isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("resolveRoute prefers the handler template over the raw uri")
    void resolveRoute_handlerPattern_isPreferred() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/projects/2f1b3c14-1f2a-4c1e-9e59-0b1d2f3a4b5c");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/projects/{id}");

        assertThat(RequestTelemetryNormalizer.resolveRoute(request)).isEqualTo("/api/projects/{id}");
    }

    @Test
    @DisplayName("resolveRoute falls back to the sanitized uri when no handler matched")
    void resolveRoute_noHandlerPattern_usesSanitizedUri() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/static/2f1b3c14-1f2a-4c1e-9e59-0b1d2f3a4b5c/app.js");

        assertThat(RequestTelemetryNormalizer.resolveRoute(request))
                .isEqualTo("/static/{id}/app.js");
    }
}
