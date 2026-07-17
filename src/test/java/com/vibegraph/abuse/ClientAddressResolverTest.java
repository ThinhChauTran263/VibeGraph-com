package com.vibegraph.abuse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressResolverTest {

    @Test
    void resolve_defaultConfiguration_ignoresSpoofedForwardingHeader() {
        AbuseProperties properties = new AbuseProperties();
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.77");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void resolve_trustedProxyConfiguration_usesFirstForwardedAddress() {
        AbuseProperties properties = new AbuseProperties();
        properties.setTrustProxy(true);
        properties.setTrustedProxies(java.util.List.of("10.0.0.4"));
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.4");
        request.addHeader("X-Forwarded-For", "198.51.100.77, 10.0.0.4");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.77");
    }
}
