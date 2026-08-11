package com.vibegraph.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.vibegraph.auth.config.JwtProperties;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    @Test
    void accessAndRefreshCookies_areSecureHttpOnlyAndUseSeparatePathsAndLifetimes() {
        JwtProperties properties = new JwtProperties();
        properties.setExpirationMs(1_800_000L);
        properties.setRefreshExpirationMs(604_800_000L);
        properties.setSecureCookies(true);
        AuthCookieService service = new AuthCookieService(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();

        String access = service.sessionCookie("access", request).toString();
        String refresh = service.refreshCookie("refresh", request).toString();

        assertThat(access).contains("vg_session=access", "Max-Age=1800", "Path=/", "HttpOnly", "Secure",
                "SameSite=Lax");
        assertThat(refresh).contains("vg_refresh=refresh", "Max-Age=604800", "Path=/api/auth", "HttpOnly",
                "Secure", "SameSite=Lax");
        assertThat(service.refreshToken(request)).isNull();
    }

    @Test
    void clearCookies_expireBothCookieNames() {
        AuthCookieService service = new AuthCookieService(new JwtProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(service.clearCookie(request).toString()).contains("vg_session=", "Max-Age=0");
        assertThat(service.clearRefreshCookie(request).toString()).contains("vg_refresh=", "Max-Age=0",
                "Path=/api/auth");
    }
}
