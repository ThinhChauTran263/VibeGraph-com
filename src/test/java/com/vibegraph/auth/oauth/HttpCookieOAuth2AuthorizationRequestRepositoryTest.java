package com.vibegraph.auth.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    @Test
    void saveLoadAndRemoveUsesOpaqueOneTimeCookie() {
        HttpCookieOAuth2AuthorizationRequestRepository repository =
                new HttpCookieOAuth2AuthorizationRequestRepository();
        OAuth2AuthorizationRequest expected = request();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(expected, request, response);

        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        String value = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        assertEquals(43, value.length());
        assertEquals(-1, value.indexOf("OAuth2AuthorizationRequest"));
        request.setCookies(new jakarta.servlet.http.Cookie("vg_oauth2_auth_request", value));

        assertEquals(expected, repository.loadAuthorizationRequest(request));
        assertEquals(expected, repository.removeAuthorizationRequest(request, new MockHttpServletResponse()));
        assertNull(repository.loadAuthorizationRequest(request));
    }

    @Test
    void malformedOrUnknownCookieIsRejected() {
        HttpCookieOAuth2AuthorizationRequestRepository repository =
                new HttpCookieOAuth2AuthorizationRequestRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("vg_oauth2_auth_request", "not-a-valid-nonce"));

        assertNull(repository.loadAuthorizationRequest(request));
        assertNull(repository.removeAuthorizationRequest(request, new MockHttpServletResponse()));
    }

    private OAuth2AuthorizationRequest request() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.example.test/oauth")
                .clientId("client")
                .redirectUri("https://app.example.test/callback")
                .scopes(Set.of("openid"))
                .state("state")
                .additionalParameters(Map.of("prompt", "login"))
                .build();
    }
}
