package com.vibegraph.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "vibegraph.auth.oauth")
public class OAuthRedirectProperties {

    private String frontendUrl = "http://localhost:5173";
    private String successPath = "/";
    private String loginPath = "/login";
}
