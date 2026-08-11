package com.vibegraph.auth.web;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;

class CookieCsrfFilterTest {

    @Test
    void stateChangingCookieRequest_withoutBrowserClientHeader_isRejected() throws Exception {
        CookieCsrfFilter filter = new CookieCsrfFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        request.setCookies(new Cookie("vg_session", "access"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void stateChangingCookieRequest_withBrowserClientHeader_continues() throws Exception {
        CookieCsrfFilter filter = new CookieCsrfFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new Cookie("vg_refresh", UUID.randomUUID().toString()));
        request.addHeader("X-VibeGraph-Client", "web");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
