package com.vibegraph.abuse;

import com.vibegraph.abuse.entity.IpBlock;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpBlockFilterTest {

    @Test
    void doFilter_blockedUnauthenticatedIp_returnsStructuredError() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        IpBlockService service = mock(IpBlockService.class);
        when(service.findActive("203.0.113.9")).thenReturn(Optional.of(block("Policy review")));
        IpBlockFilter filter = new IpBlockFilter(new ClientAddressResolver(properties), service, new ObjectMapper());
        MockHttpServletRequest request = request("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("IP_BLOCKED", "Policy review");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void doFilter_spoofedForwardedHeader_cannotEvadeExactBlockByDefault() throws Exception {
        AbuseProperties properties = new AbuseProperties();
        IpBlockService service = mock(IpBlockService.class);
        when(service.findActive("203.0.113.9")).thenReturn(Optional.of(block("Blocked")));
        IpBlockFilter filter = new IpBlockFilter(new ClientAddressResolver(properties), service, new ObjectMapper());
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.44");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest request(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        request.setRemoteAddr(address);
        return request;
    }

    private IpBlock block(String reason) {
        return IpBlock.builder().ipAddress("203.0.113.9").safeReason(reason)
                .active(true).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
