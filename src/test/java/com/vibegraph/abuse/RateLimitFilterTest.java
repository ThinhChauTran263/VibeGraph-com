package com.vibegraph.abuse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitFilterTest {

    @Test
    void doFilter_requestsBeyondIpLimit_returnsStructured429() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1);
        properties.setRequestsPerMinutePerUser(100);
        RequestEventService eventService = mock(RequestEventService.class);
        RateLimitFilter filter = new RateLimitFilter(
                properties,
                new ClientAddressResolver(properties),
                eventService,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-16T10:00:00Z"), ZoneOffset.UTC));

        MockHttpServletRequest first = request("203.0.113.5");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());

        MockHttpServletRequest second = request("203.0.113.5");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(second, secondResponse, secondChain);

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(secondChain.getRequest()).isNull();
    }

    @Test
    void doFilter_spoofedForwardedFor_doesNotCreateAnotherDefaultBucket() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        properties.setRequestsPerMinutePerIp(1);
        RateLimitFilter filter = new RateLimitFilter(
                properties,
                new ClientAddressResolver(properties),
                mock(RequestEventService.class),
                new ObjectMapper(),
                Clock.systemUTC());

        MockHttpServletRequest first = request("203.0.113.5");
        first.addHeader("X-Forwarded-For", "198.51.100.1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest second = request("203.0.113.5");
        second.addHeader("X-Forwarded-For", "198.51.100.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
