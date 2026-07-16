package com.vibegraph.auth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.FilterChainProxy;
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
import com.vibegraph.auth.service.AdminService;
import com.vibegraph.auth.service.AdminStorageService;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @MockitoBean private AdminStorageService adminStorageService;
    @MockitoBean private CreditBalanceService creditBalanceService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private AccountSettingsService accountSettingsService;

    @MockitoBean private UserRepository userRepository;
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
}
