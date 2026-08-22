package com.vibegraph.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.cli.CliDeviceAuthorization;
import com.vibegraph.auth.cli.CliDeviceAuthorizationController;
import com.vibegraph.auth.cli.CliDeviceAuthorizationProperties;
import com.vibegraph.auth.cli.CliDeviceAuthorizationRepository;
import com.vibegraph.auth.cli.CliDeviceAuthorizationService;
import com.vibegraph.auth.cli.CliDeviceAuthorizationStatus;
import com.vibegraph.auth.domain.ApiKey;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectOwnershipStatus;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.repository.ApiKeyRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.service.AccountAccessGuard;
import com.vibegraph.auth.service.AccountSettingsService;
import com.vibegraph.auth.service.ApiKeySecretProtector;
import com.vibegraph.auth.service.ApiKeyService;
import com.vibegraph.auth.service.AuditService;
import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.auth.service.CreditBalanceService;
import com.vibegraph.auth.service.CreditPricingService;
import com.vibegraph.auth.service.FeatureGateService;
import com.vibegraph.auth.web.ApiKeyAuthFilter;
import com.vibegraph.auth.web.ApiKeyRequestContextAccessor;
import com.vibegraph.common.exception.GlobalExceptionHandler;
import com.vibegraph.common.ownership.ProjectOwnershipGuard;
import com.vibegraph.graph.service.CliRepositoryService;
import com.vibegraph.mcp.MeteredToolCallback;

import jakarta.servlet.FilterChain;

/**
 * Deterministic cross-module contract test for the production CLI-to-MCP credential hand-off.
 *
 * <p>This is not a browser or deployed-server E2E. It runs MockMvc against the real device
 * controller/service, uses the real selected-key ownership logic and API-key authentication
 * filter, then invokes the real MCP authorization/metering decorator. Persistence ports and the
 * final credit service are mocked so the critical contract always runs even when Docker is off.
 * PostgreSQL debit/ledger atomicity remains covered by {@code CreditDebitConcurrencyTest} when
 * Testcontainers is available.
 */
@DisplayName("CLI device authorization -> project-bound MCP credit flow")
class CliMcpCreditFlowTest {

    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID API_KEY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PROJECT_ID = "cli-mcp-e2e-project";
    private static final String PROJECT_NAME = "CLI MCP E2E";
    private static final String API_KEY = "vbg_cli_mcp_e2e_secret";
    private static final String API_KEY_PREFIX = "vbg_cli_mcp_";
    private static final String VERIFIER = "v".repeat(48);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CurrentUser currentUser = new CurrentUser();
    private final CliDeviceAuthorizationRepository deviceRepository =
            mock(CliDeviceAuthorizationRepository.class);
    private final ApiKeyRepository apiKeyRepository = mock(ApiKeyRepository.class);
    private final ProjectOwnershipRepository projectRepository = mock(ProjectOwnershipRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountSettingsService accountSettingsService = mock(AccountSettingsService.class);
    private final FeatureGateService featureGateService = mock(FeatureGateService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ApiKeySecretProtector secretProtector = mock(ApiKeySecretProtector.class);
    private final CliRepositoryService cliRepositoryService = mock(CliRepositoryService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AtomicReference<CliDeviceAuthorization> authorizationState = new AtomicReference<>();

    private MockMvc mockMvc;
    private User user;
    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(USER_ID)
                .email("cli-mcp-e2e@example.test")
                .role(Role.USER)
                .createdAt(NOW.minusSeconds(86_400))
                .build();
        ProjectOwnership project = ProjectOwnership.builder()
                .projectId(PROJECT_ID)
                .ownerId(USER_ID)
                .name(PROJECT_NAME)
                .sourceType(ProjectSourceType.LOCAL)
                .status(ProjectOwnershipStatus.ANALYZED)
                .build();
        apiKey = ApiKey.builder()
                .id(API_KEY_ID)
                .userId(USER_ID)
                .projectId(PROJECT_ID)
                .keyHash(passwordEncoder.encode(API_KEY))
                .keyPrefix(API_KEY_PREFIX)
                .secretCipher("stored-key-cipher")
                .name("CLI MCP")
                .build();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(API_KEY_ID, USER_ID))
                .thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(List.of(apiKey));
        when(projectRepository.findByProjectIdAndOwnerIdAndDeletedAtIsNull(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(project));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.findOwnerId(PROJECT_ID)).thenReturn(Optional.of(USER_ID));
        when(secretProtector.decrypt("stored-key-cipher")).thenReturn(API_KEY);
        when(secretProtector.encrypt(API_KEY)).thenReturn("device-key-cipher");
        when(secretProtector.decrypt("device-key-cipher")).thenReturn(API_KEY);

        ApiKeyService apiKeyService = new ApiKeyService(
                currentUser,
                apiKeyRepository,
                projectRepository,
                userRepository,
                accountSettingsService,
                passwordEncoder,
                featureGateService,
                auditService,
                secretProtector);

        when(deviceRepository.save(any(CliDeviceAuthorization.class))).thenAnswer(invocation -> {
            CliDeviceAuthorization value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
            }
            authorizationState.set(value);
            return value;
        });
        when(deviceRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(authorizationState.get()));
        when(deviceRepository.findByDeviceCodeHashForUpdate(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(authorizationState.get()));

        CliDeviceAuthorizationService deviceService = new CliDeviceAuthorizationService(
                deviceRepository,
                currentUser,
                apiKeyService,
                cliRepositoryService,
                new CliDeviceAuthorizationProperties("https://app.vibegraph.com", 600, 1),
                Clock.fixed(NOW, ZoneOffset.UTC),
                secretProtector);
        mockMvc = MockMvcBuilders.standaloneSetup(new CliDeviceAuthorizationController(deviceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("owned key exchanges once, authenticates MCP, and debits its bound project once")
    void ownedKey_exchangeOnce_authenticatesAndDebitsBoundProject() throws Exception {
        JsonNode started = startDevice(API_KEY_ID);
        authenticateBrowserUser();

        approve(started, API_KEY_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID));

        MvcResult exchangeResult = exchange(started)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.apiKey").value(API_KEY))
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.availableKeys[0].id").value(API_KEY_ID.toString()))
                .andReturn();
        JsonNode exchanged = objectMapper.readTree(
                exchangeResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");

        assertThat(authorizationState.get().getStatus()).isEqualTo(CliDeviceAuthorizationStatus.CONSUMED);
        exchange(started)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONSUMED"))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());

        SecurityContextHolder.clearContext();
        CreditBalanceService creditBalanceService = mock(CreditBalanceService.class);
        CreditPricingService creditPricingService = mock(CreditPricingService.class);
        AccountAccessGuard accountAccessGuard = mock(AccountAccessGuard.class);
        ToolCallback delegate = projectTool();
        when(creditPricingService.calculateCredits("MCP_TOOL_CALL", 0, 0)).thenReturn(2L);
        when(delegate.call(anyString())).thenReturn("architecture");

        MeteredToolCallback callback = meteredCallback(
                delegate, creditPricingService, creditBalanceService, accountAccessGuard);
        ApiKeyAuthFilter apiKeyFilter = apiKeyFilter();
        MockHttpServletRequest mcpRequest = new MockHttpServletRequest("POST", "/mcp");
        mcpRequest.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, exchanged.path("apiKey").asText());
        MockHttpServletResponse mcpResponse = new MockHttpServletResponse();
        AtomicReference<String> toolResult = new AtomicReference<>();
        FilterChain invokeTool = (request, response) -> {
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes((MockHttpServletRequest) request));
            toolResult.set(callback.call("{\"projectId\":\"" + PROJECT_ID + "\"}"));
        };

        apiKeyFilter.doFilter(mcpRequest, mcpResponse, invokeTool);

        assertThat(mcpResponse.getStatus()).isEqualTo(200);
        assertThat(toolResult).hasValue("architecture");
        verify(creditBalanceService).deductCredits(
                USER_ID, 2L, "MCP", "MCP_TOOL_CALL", PROJECT_ID);
        verify(delegate).call("{\"projectId\":\"" + PROJECT_ID + "\"}");
    }

    @Test
    @DisplayName("another user's selected key is rejected and the device remains pending")
    void foreignSelectedKey_isRejectedBeforeApproval() throws Exception {
        UUID foreignKeyId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        JsonNode started = startDevice(null);
        authenticateBrowserUser();

        approve(started, foreignKeyId)
                .andExpect(status().isForbidden());

        assertThat(authorizationState.get().getStatus()).isEqualTo(CliDeviceAuthorizationStatus.PENDING);
        assertThat(authorizationState.get().getApiKeyId()).isNull();
        verify(secretProtector, never()).encrypt(API_KEY);
    }

    @Test
    @DisplayName("MCP project mismatch is rejected before pricing, debit, and tool execution")
    void apiKeyProjectMismatch_isRejectedBeforeDebit() throws Exception {
        JsonNode started = startDevice(API_KEY_ID);
        authenticateBrowserUser();
        approve(started, API_KEY_ID).andExpect(status().isOk());
        MvcResult exchangeResult = exchange(started).andExpect(status().isOk()).andReturn();
        JsonNode exchanged = objectMapper.readTree(
                exchangeResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        SecurityContextHolder.clearContext();

        CreditBalanceService creditBalanceService = mock(CreditBalanceService.class);
        CreditPricingService creditPricingService = mock(CreditPricingService.class);
        AccountAccessGuard accountAccessGuard = mock(AccountAccessGuard.class);
        ToolCallback delegate = projectTool();
        MeteredToolCallback callback = meteredCallback(
                delegate, creditPricingService, creditBalanceService, accountAccessGuard);
        ApiKeyAuthFilter apiKeyFilter = apiKeyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, exchanged.path("apiKey").asText());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain invokeWrongProject = (servletRequest, servletResponse) -> {
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes((MockHttpServletRequest) servletRequest));
            callback.call("{\"projectId\":\"another-project\"}");
        };

        assertThatThrownBy(() -> apiKeyFilter.doFilter(request, response, invokeWrongProject))
                .isInstanceOf(com.vibegraph.common.exception.ForbiddenException.class)
                .hasMessage("Access denied");
        verify(creditPricingService, never()).calculateCredits(anyString(), anyInt(), anyLong());
        verify(creditBalanceService, never()).deductCredits(
                any(UUID.class), anyLong(), anyString(), anyString(), anyString());
        verify(delegate, never()).call(anyString());
    }

    private JsonNode startDevice(UUID preferredKeyId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("codeChallenge", pkceChallenge(VERIFIER));
        request.put("deviceName", "Local E2E");
        request.put("client", "test");
        request.put("intent", "MCP");
        if (preferredKeyId != null) {
            request.put("preferredApiKeyId", preferredKeyId);
        }
        MvcResult result = mockMvc.perform(post("/api/cli/device/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationUri")
                        .value("https://app.vibegraph.com/cli/authorize"))
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private ResultActions approve(JsonNode started, UUID keyId) throws Exception {
        return mockMvc.perform(post("/api/cli/device/{id}/approve", started.path("requestId").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "browserSecret", browserSecret(started),
                        "projectMode", "KEY",
                        "apiKeyId", keyId))));
    }

    private ResultActions exchange(JsonNode started) throws Exception {
        return mockMvc.perform(post("/api/cli/device/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "deviceCode", started.path("deviceCode").asText(),
                        "pollToken", started.path("pollToken").asText(),
                        "codeVerifier", VERIFIER))));
    }

    private ToolCallback projectTool() {
        ToolCallback delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(DefaultToolDefinition.builder()
                .name("get_project_architecture")
                .description("Project-scoped integration tool")
                .inputSchema("""
                        {"type":"object","properties":{"projectId":{"type":"string"}},
                         "required":["projectId"]}
                        """)
                .build());
        return delegate;
    }

    private MeteredToolCallback meteredCallback(
            ToolCallback delegate,
            CreditPricingService creditPricingService,
            CreditBalanceService creditBalanceService,
            AccountAccessGuard accountAccessGuard) {
        ProjectOwnershipGuard ownershipGuard = new ProjectOwnershipGuard(projectRepository, currentUser);
        return new MeteredToolCallback(
                delegate,
                currentUser,
                creditPricingService,
                creditBalanceService,
                ownershipGuard,
                featureGateService,
                accountAccessGuard,
                new ApiKeyRequestContextAccessor(),
                objectMapper);
    }

    private ApiKeyAuthFilter apiKeyFilter() {
        when(apiKeyRepository.findTop6ByKeyPrefixAndDeletedAtIsNullAndDisabledAtIsNullOrderByIdAsc(
                API_KEY_PREFIX)).thenReturn(List.of(apiKey));
        return new ApiKeyAuthFilter(
                apiKeyRepository,
                userRepository,
                projectRepository,
                accountSettingsService,
                passwordEncoder);
    }

    private String browserSecret(JsonNode started) {
        String complete = started.path("verificationUriComplete").asText();
        return complete.substring(complete.indexOf("#secret=") + "#secret=".length());
    }

    private void authenticateBrowserUser() {
        AuthenticatedUser principal = new AuthenticatedUser(USER_ID, user.getEmail(), Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static String pkceChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
