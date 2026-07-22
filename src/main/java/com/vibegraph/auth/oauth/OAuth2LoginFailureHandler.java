package com.vibegraph.auth.oauth;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Set<String> SAFE_ERROR_CODES = Set.of(
            "oauth_email_unavailable",
            "oauth_email_unverified",
            "oauth_account_link_failed");

    private final OAuthRedirects redirects;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        authorizationRequestRepository.removeAuthorizationRequest(request, response);
        redirectStrategy.sendRedirect(request, response, redirects.loginErrorUrl(safeErrorCode(exception)));
    }

    private String safeErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String code = oauthException.getError().getErrorCode();
            if (SAFE_ERROR_CODES.contains(code)) {
                return code;
            }
        }
        return "oauth_failed";
    }
}
