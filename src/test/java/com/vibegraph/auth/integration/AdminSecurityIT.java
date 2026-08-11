package com.vibegraph.auth.integration;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.neo4j.driver.Driver;
import com.vibegraph.auth.repository.*;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.AdminAnnouncementService;
import com.vibegraph.auth.service.AdminFeatureFlagService;
import com.vibegraph.auth.service.AdminPlanManagementService;
import com.vibegraph.auth.service.AdminPricingManagementService;
import com.vibegraph.auth.service.AdminSecurityMonitorService;
import com.vibegraph.auth.service.AdminSecurityRequestEventStream;
import com.vibegraph.auth.service.AuditLogEventStream;
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.auth.service.AdminStorageService;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AdminSecurityIT.TestConfig.class)
@DisplayName("Admin Security Integration")
class AdminSecurityIT {

    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration"
    })
    @org.springframework.context.annotation.ComponentScan(basePackages = {
        "com.vibegraph.auth.config",
        "com.vibegraph.auth.web",
        "com.vibegraph.auth.service"
    })
    static class TestConfig {
        /**
         * AuthController needs the sign-in failure budget, which lives in {@code com.vibegraph.abuse}
         * and is outside this slice's component scan. Constructed explicitly rather than widening the
         * scan, which would drag in the rate-limit filter and the telemetry pipeline with it.
         */
        @org.springframework.context.annotation.Bean
        public com.vibegraph.abuse.LoginThrottleGuard loginThrottleGuard() {
            return new com.vibegraph.abuse.LoginThrottleGuard(
                    new com.vibegraph.abuse.AbuseProperties(), java.time.Clock.systemUTC());
        }

        @org.springframework.context.annotation.Bean
        public jakarta.persistence.EntityManagerFactory entityManagerFactory() {
            return org.mockito.Mockito.mock(jakarta.persistence.EntityManagerFactory.class);
        }

        @org.springframework.context.annotation.Bean
        public jakarta.persistence.EntityManager entityManager() {
            return org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockitoBean private Driver neo4jDriver;
    @MockitoBean private AdminService adminService;
    @MockitoBean private AdminPlanManagementService adminPlanManagementService;
    @MockitoBean private AdminPricingManagementService adminPricingManagementService;
    @MockitoBean private AdminFeatureFlagService adminFeatureFlagService;
    @MockitoBean private AdminAnnouncementService adminAnnouncementService;
    @MockitoBean private AdminSecurityMonitorService adminSecurityMonitorService;
    @MockitoBean private AdminSecurityRequestEventStream adminSecurityRequestEventStream;
    @MockitoBean private AuditLogEventStream auditLogEventStream;
    @MockitoBean private AdminStorageService adminStorageService;
    @MockitoBean private CreditBalanceService creditBalanceService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AccountSettingsService accountSettingsService;
    @MockitoBean private AuthenticationFailureHandler authenticationFailureHandler;
    @MockitoBean private AuthenticationSuccessHandler authenticationSuccessHandler;
    @MockitoBean private OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService;
    @MockitoBean private AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private RefreshSessionRepository refreshSessionRepository;
    @MockitoBean private UserAccountSettingsRepository settingsRepository;
    @MockitoBean private ProjectOwnershipRepository projectOwnershipRepository;
    @MockitoBean private FeedbackReportRepository feedbackReportRepository;
    @MockitoBean private FeedbackMessageRepository feedbackMessageRepository;
    @MockitoBean private PlanRepository planRepository;
    @MockitoBean private UserCreditBalanceRepository creditBalanceRepository;
    @MockitoBean private CreditPricingRuleRepository pricingRuleRepository;
    @MockitoBean private CreditLedgerRepository creditLedgerRepository;
    @MockitoBean private ApiKeyRepository apiKeyRepository;
    @MockitoBean private FeatureFlagRepository featureFlagRepository;
    @MockitoBean private ProjectUsageRepository projectUsageRepository;
    @MockitoBean private jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @MockitoBean private jakarta.persistence.EntityManager entityManager;
    @MockitoBean private com.vibegraph.auth.CurrentUser currentUser;
    @MockitoBean private com.vibegraph.common.config.CorsProperties corsProperties;
    @MockitoBean private com.vibegraph.abuse.IpBlockService ipBlockService;
    @MockitoBean private com.vibegraph.abuse.RequestEventService requestEventService;
    @MockitoBean private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @MockitoBean private com.vibegraph.auth.repository.AuditLogRepository auditLogRepository;
    @MockitoBean private com.vibegraph.auth.repository.AuditRetentionSettingRepository auditRetentionSettingRepository;
    @MockitoBean private com.vibegraph.auth.repository.NotificationRepository notificationRepository;
    @MockitoBean private com.vibegraph.auth.repository.SecurityEventRepository securityEventRepository;
    @MockitoBean private com.vibegraph.auth.repository.AnnouncementRepository announcementRepository;
    @MockitoBean private com.vibegraph.auth.repository.UserIdentityRepository userIdentityRepository;
    @MockitoBean private org.springframework.messaging.simp.SimpMessagingTemplate simpMessagingTemplate;
    @MockitoBean private java.time.Clock clock;

    @Test
    @DisplayName("GET /api/admin/overview without authenticated user returns 401 Unauthorized")
    void getOverview_unauthenticated_returnsUnauthorizedOrForbidden() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/admin/overview with USER role returns 403 Forbidden")
    void getOverview_userRole_returnsForbidden() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/overview with ADMIN role succeeds")
    void getOverview_adminRole_succeeds() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/admin/feature-flags with USER role returns 403")
    void featureFlags_userRole_returnsForbidden() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/feature-flags"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/feature-flags with ADMIN role reaches controller")
    void featureFlags_adminRole_reachesController() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/feature-flags"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/feature-flags with ADMIN role reaches controller")
    void featureFlagsCreate_adminRole_reachesController() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(post("/api/admin/feature-flags")
                        .contentType("application/json")
                        .content("{\"key\":\"registration\",\"scope\":\"GLOBAL\",\"displayName\":\"Registration\",\"enabled\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/admin/security/stream with USER role returns 403")
    void securityStream_userRole_returnsForbidden() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/security/stream"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/security/stream with ADMIN role opens SSE")
    void securityStream_adminRole_opensSse() throws Exception {
        org.mockito.Mockito.when(adminSecurityRequestEventStream.subscribe())
                .thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/security/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("ASYNC dispatch for an SSE disconnect does not require a second authentication")
    void securityStream_asyncDispatch_isAllowedAfterDisconnect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/security/stream");
        request.setDispatcherType(DispatcherType.ASYNC);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        springSecurityFilterChain.doFilter(request, response, chain);

        org.mockito.Mockito.verify(chain).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getStatus()).isNotEqualTo(403);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("GET /api/admin/audit-logs/stream with USER role returns 403")
    void auditStream_userRole_returnsForbidden() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/audit-logs/stream"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/audit-logs/stream with ADMIN role opens SSE")
    void auditStream_adminRole_opensSse() throws Exception {
        org.mockito.Mockito.when(auditLogEventStream.subscribe())
                .thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/api/admin/audit-logs/stream"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }
}
