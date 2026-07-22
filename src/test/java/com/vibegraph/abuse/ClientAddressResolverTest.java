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

    @Test
    void resolve_untrustedRemoteProxy_ignoresForwardedAddress() {
        AbuseProperties properties = new AbuseProperties();
        properties.setTrustProxy(true);
        properties.setTrustedProxies(java.util.List.of("10.0.0.4"));
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.6");
        request.addHeader("X-Forwarded-For", "198.51.100.77");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.6");
    }

    @Test
    void resolve_trustedDockerCidr_usesForwardedClientAddress() {
        AbuseProperties properties = new AbuseProperties();
        properties.setTrustProxy(true);
        properties.setTrustedProxies(java.util.List.of("172.18.0.0/16"));
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.6");
        request.addHeader("X-Forwarded-For", "198.51.100.77, 172.18.0.6");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.77");
    }

    @Test
    void resolve_invalidForwardedAddress_fallsBackToRemoteAddress() {
        AbuseProperties properties = new AbuseProperties();
        properties.setTrustProxy(true);
        properties.setTrustedProxies(java.util.List.of("172.18.0.0/16"));
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.6");
        request.addHeader("X-Forwarded-For", "not-an-ip, 10.0.0.5");

        assertThat(resolver.resolve(request)).isEqualTo("172.18.0.6");
    }

    @Test
    void resolve_privateForwardedAddress_skipsToFirstPublicAddress() {
        AbuseProperties properties = new AbuseProperties();
        properties.setTrustProxy(true);
        properties.setTrustedProxies(java.util.List.of("172.16.0.0/12"));
        ClientAddressResolver resolver = new ClientAddressResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.6");
        request.addHeader("X-Forwarded-For", "10.0.0.5, 198.51.100.77, 172.18.0.6");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.77");
    }

    @Test
    void canonicalize_ipv4Address_remainsStable() {
        assertThat(ClientAddressResolver.canonicalize("203.0.113.10")).isEqualTo("203.0.113.10");
    }
}
