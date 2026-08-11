package com.vibegraph.auth.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class StatelessSessionCookieFilterTest {

    @Test
    void clearsJsSessionIdCookieWhenPresentOnRequest() throws Exception {
        StatelessSessionCookieFilter filter = new StatelessSessionCookieFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("JSESSIONID", "legacy-session-id"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(header -> assertThat(header).contains("JSESSIONID="))
                .anySatisfy(header -> assertThat(header).contains("Max-Age=0"));
    }

    @Test
    void clearsJsSessionIdCookieWhenResponseStartsToSetIt() throws Exception {
        StatelessSessionCookieFilter filter = new StatelessSessionCookieFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((jakarta.servlet.http.HttpServletResponse) servletResponse)
                        .addHeader(HttpHeaders.SET_COOKIE, "JSESSIONID=server-session"));

        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(header -> assertThat(header).contains("JSESSIONID="))
                .anySatisfy(header -> assertThat(header).contains("Max-Age=0"));
    }
}
