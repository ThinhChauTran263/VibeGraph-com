package com.vibegraph.auth.oauth;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.vibegraph.auth.dto.AuthResponse;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.config.JwtProperties;
import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OAuth2 login success handler")
class OAuth2LoginSuccessHandlerTest {

    @Test
    @DisplayName("success handler issues the same auth cookie and redirects users to their dashboard")
    void onAuthenticationSuccess_setsCookieAndRedirects() throws Exception {
        AuthService authService = mock(AuthService.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpirationMs(86_400_000L);
        AuthCookieService cookieService = new AuthCookieService(jwtProperties);
        OAuthRedirectProperties redirectProperties = new OAuthRedirectProperties();
        redirectProperties.setFrontendUrl("http://frontend.local");
        redirectProperties.setLoginPath("/login");
        redirectProperties.setSuccessPath("/");
        OAuthRedirects redirects = new OAuthRedirects(redirectProperties);
        HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository =
                new HttpCookieOAuth2AuthorizationRequestRepository();
        OAuth2LoginFailureHandler failureHandler = mock(OAuth2LoginFailureHandler.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                authService,
                cookieService,
                authorizationRequestRepository,
                redirects,
                failureHandler);

        Map<String, Object> attributes = Map.of(
                "sub", "google-subject",
                "email", "oauth@test.local",
                "email_verified", true,
                "name", "OAuth User",
                "picture", "https://avatar.test/user.png");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub"),
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                "google");
        when(authService.oauthLogin(any())).thenReturn(new AuthResponse(
                "jwt-token",
                new UserResponse(UUID.randomUUID().toString(), "oauth@test.local", "OAuth User", "USER", "ACTIVE", null)));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("http://frontend.local/dashboard", response.getRedirectedUrl());
        assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(value -> value.contains("vg_session=jwt-token")));
        verify(authService).oauthLogin(any());
    }
}
