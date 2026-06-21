package com.vibegraph.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("ApiKeyFilter")
class ApiKeyFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ApiKeyFilter filter(String key) {
        ApiKeyProperties props = new ApiKeyProperties();
        props.setApiKey(key);
        return new ApiKeyFilter(props, objectMapper);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        return req;
    }

    @Test
    @DisplayName("blank key disables the gate: guarded endpoint passes without a header")
    void disabledWhenKeyBlank() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/projects/import-local");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter("").doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("enabled gate rejects a guarded endpoint with no key (401)")
    void rejectsMissingKey() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/projects/import-local");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("secret").doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("enabled gate rejects a wrong key (401)")
    void rejectsWrongKey() throws Exception {
        MockHttpServletRequest req = request("GET", "/api/projects/browse");
        req.addHeader("X-API-Key", "nope");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("secret").doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("enabled gate passes a guarded endpoint with the correct key")
    void passesCorrectKey() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/projects/import-archive");
        req.addHeader("X-API-Key", "secret");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter("secret").doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("unguarded endpoints (e.g. GET graph) are never gated, even with a key set")
    void leavesUnguardedEndpointsOpen() throws Exception {
        MockHttpServletRequest req = request("GET", "/api/projects/p1/graph");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter("secret").doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(200);
    }
}
