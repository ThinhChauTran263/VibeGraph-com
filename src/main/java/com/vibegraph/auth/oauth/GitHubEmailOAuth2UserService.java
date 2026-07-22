package com.vibegraph.auth.oauth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubEmailOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        if (!"github".equalsIgnoreCase(userRequest.getClientRegistration().getRegistrationId())
                || user.getAttribute("email") != null) {
            return user;
        }
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        primaryVerifiedEmail(userRequest).ifPresent(email -> {
            attributes.put("email", email.email());
            attributes.put("email_verified", email.verified());
        });
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, userNameAttributeName);
    }

    private java.util.Optional<GitHubEmail> primaryVerifiedEmail(OAuth2UserRequest userRequest) {
        GitHubEmail[] emails = restClient.get()
                .uri(GITHUB_EMAILS_URL)
                .headers(headers -> headers.setBearerAuth(userRequest.getAccessToken().getTokenValue()))
                .retrieve()
                .body(GitHubEmail[].class);
        if (emails == null) {
            return java.util.Optional.empty();
        }
        return java.util.Arrays.stream(emails)
                .filter(email -> email.primary() && email.verified() && email.email() != null)
                .findFirst();
    }

    private record GitHubEmail(String email, boolean primary, boolean verified) {
    }
}
