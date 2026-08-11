package com.vibegraph.auth.oauth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.stereotype.Component;

import com.vibegraph.auth.service.AuthCookieService;
import com.vibegraph.auth.service.AuthenticationResult;
import com.vibegraph.auth.service.AuthService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final OAuthRedirects redirects;
    private final OAuth2LoginFailureHandler failureHandler;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Authentication)) {
            failureHandler.onAuthenticationFailure(request, response,
                    new OAuth2AuthenticationException(new OAuth2Error("oauth_failed")));
            return;
        }
        try {
            authorizationRequestRepository.removeAuthorizationRequest(request, response);
            AuthenticationResult authResult = authService.oauthLoginSession(
                    OAuthProfileExtractor.from(oauth2Authentication));
            response.addHeader(HttpHeaders.SET_COOKIE,
                    authCookieService.sessionCookie(authResult.accessToken(), request).toString());
            response.addHeader(HttpHeaders.SET_COOKIE,
                    authCookieService.refreshCookie(authResult.refreshToken(), request).toString());
            String role = authResult.user() != null ? authResult.user().role() : null;
            redirectStrategy.sendRedirect(request, response, redirects.successUrlForRole(role));
        } catch (OAuth2AuthenticationException ex) {
            log.warn("OAuth login failed during local account mapping: {}", ex.getError().getErrorCode());
            failureHandler.onAuthenticationFailure(request, response, ex);
        } catch (RuntimeException ex) {
            log.warn("OAuth login failed during local account mapping");
            failureHandler.onAuthenticationFailure(request, response,
                    new OAuth2AuthenticationException(new OAuth2Error("oauth_failed")));
        }
    }
}
