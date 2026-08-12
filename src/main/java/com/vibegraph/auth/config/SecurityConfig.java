package com.vibegraph.auth.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.abuse.AbuseProperties;
import com.vibegraph.abuse.ClientAddressResolver;
import com.vibegraph.abuse.IpBlockFilter;
import com.vibegraph.abuse.IpBlockService;
import com.vibegraph.abuse.RateLimitFilter;
import com.vibegraph.abuse.RequestEventService;
import com.vibegraph.auth.web.ApiKeyAuthFilter;
import com.vibegraph.auth.web.CookieCsrfFilter;
import com.vibegraph.auth.web.JwtAuthFilter;
import com.vibegraph.auth.web.RestAccessDeniedHandler;
import com.vibegraph.auth.web.RestAuthEntryPoint;
import com.vibegraph.auth.web.StatelessSessionCookieFilter;
import com.vibegraph.auth.oauth.OAuthRedirectProperties;
import com.vibegraph.common.config.CorsProperties;

import jakarta.servlet.DispatcherType;

import lombok.RequiredArgsConstructor;

/**
 * Explicit, stateless security policy. Browser JWT cookies are protected by the custom client
 * header CSRF boundary; bearer/API-key clients remain stateless and do not need a CSRF token.
 *
 * <p>Permit list is deliberately narrow: {@code /api/auth/**}, {@code /actuator/health}, the
 * WebSocket transport handshake, and CORS preflight. STOMP sessions authenticate independently on
 * {@code CONNECT}; {@code /mcp/**} requires HTTP authentication unless the explicit
 * {@code vibegraph.auth.realtime.demo-permit} flag is set. Everything else requires authentication.
 *
 * <p>CORS is driven from the same {@link CorsProperties} allow-list the app already uses (no
 * wildcard with credentials), wired into the security chain so preflight is handled before authz.
 *
 * <p>This slice does not touch project/ownership endpoints; the ownership guard lands separately.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({
        JwtProperties.class,
        RealtimeSecurityProperties.class,
        AbuseProperties.class,
        OAuthRedirectProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthFilter jwtAuthFilter;
    private final CookieCsrfFilter cookieCsrfFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final RestAuthEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RealtimeSecurityProperties realtimeProperties;
    private final CorsProperties corsProperties;
    private final AbuseProperties abuseProperties;
    private final IpBlockService ipBlockService;
    private final RequestEventService requestEventService;
    private final ObjectMapper objectMapper;
    private final AuthenticationSuccessHandler oAuth2LoginSuccessHandler;
    private final AuthenticationFailureHandler oAuth2LoginFailureHandler;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService;
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository;
    private final StatelessSessionCookieFilter statelessSessionCookieFilter;

    @Bean
    public ClientAddressResolver clientAddressResolver() {
        return new ClientAddressResolver(abuseProperties);
    }

    @Bean
    public IpBlockFilter ipBlockFilter(ClientAddressResolver resolver) {
        return new IpBlockFilter(resolver, ipBlockService, objectMapper);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(ClientAddressResolver resolver,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new RateLimitFilter(abuseProperties, resolver, requestEventService, objectMapper,
                java.time.Clock.systemUTC(), meterRegistry);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) throws Exception {
        boolean demoPermit = realtimeProperties.isDemoPermit();
        if (demoPermit) {
            log.warn("SECURITY: vibegraph.auth.realtime.demo-permit=true — /mcp/** is "
                    + "PERMITTED WITHOUT HTTP AUTHENTICATION. This is for demo/local only and is NOT "
                    + "multi-user safe. STOMP connections require either a Bearer token or an authenticated "
                    + "browser cookie handshake.");
        }

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        // No Content-Security-Policy here on purpose. CSP only takes effect when the
                        // browser builds a document, and this service never returns one: /error
                        // answers JSON even for Accept: text/html, and the OAuth redirect has no
                        // body. A header that protects nothing is worse than none — a reviewer sees
                        // "CSP present" and stops asking where the real one is.
                        //
                        // The policy that matters belongs to whatever serves the SPA:
                        // vibegraph-web/nginx.conf.template. Add one here too if this application
                        // ever starts returning HTML (Swagger UI, an admin console, or the SPA
                        // packaged into the jar).
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Sent only over HTTPS by Spring, so it stays inert in local HTTP dev.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), interest-cohort=()")))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(sc -> sc.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    // MVC completes SseEmitter requests through an ASYNC dispatch after a
                    // client disconnects. The initial request is still authenticated below;
                    // re-authorizing the internal dispatch can only produce a late 403 after
                    // the response has already been committed.
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(
                            "/api/auth/**",
                            "/actuator/health",
                            "/ws/**",
                            "/oauth2/**",
                            "/login/oauth2/**").permitAll();
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    if (demoPermit) {
                        auth.requestMatchers("/mcp/**").permitAll();
                    } else {
                        auth.requestMatchers("/mcp/**").hasAuthority("API_KEY");
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(oAuth2AuthorizationRequestRepository))
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                .addFilterBefore(statelessSessionCookieFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(ipBlockFilter(clientAddressResolver()), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(cookieCsrfFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAt(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter(clientAddressResolver(), meterRegistry), org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS from the shared {@link CorsProperties} allow-list. {@code allowCredentials(true)}
     * with an explicit origin list (never "*", which {@code CorsConfig} already rejects at startup).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
