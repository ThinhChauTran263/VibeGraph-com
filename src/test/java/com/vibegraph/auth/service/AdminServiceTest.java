package com.vibegraph.auth.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.domain.FeedbackCategory;
import com.vibegraph.auth.domain.entity.FeedbackMessage;
import com.vibegraph.auth.domain.entity.FeedbackReport;
import com.vibegraph.auth.domain.FeedbackReportStatus;
import com.vibegraph.auth.domain.FeedbackSenderRole;
import com.vibegraph.auth.domain.entity.Plan;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.domain.entity.UserAccountSettings;
import com.vibegraph.auth.dto.AdminCreateUserRequest;
import com.vibegraph.auth.dto.AdminFeedbackReplyRequest;
import com.vibegraph.auth.dto.AdminOverviewResponse;
import com.vibegraph.auth.dto.AdminStorageOverviewResponse;
import com.vibegraph.auth.dto.AdminUserBlockRequest;
import com.vibegraph.auth.dto.AdminUserResponse;
import com.vibegraph.auth.dto.AdminUserUpdatePlanRequest;
import com.vibegraph.auth.dto.AdminUserUpdateQuotaRequest;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;
import com.vibegraph.auth.repository.FeedbackMessageRepository;
import com.vibegraph.auth.repository.FeedbackReportRepository;
import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;
import com.vibegraph.auth.repository.UserCreditBalanceRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.auth.repository.projection.AdminSeriesRow;
import com.vibegraph.common.exception.EmailAlreadyExistsException;
import com.vibegraph.common.exception.QuotaBelowCurrentUsageException;
import com.vibegraph.auth.dto.StorageUnknownResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService")
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAccountSettingsRepository settingsRepository;
    @Mock private ProjectOwnershipRepository projectOwnershipRepository;
    @Mock private com.vibegraph.auth.repository.ProjectUsageRepository projectUsageRepository;
    @Mock private FeedbackReportRepository feedbackReportRepository;
    @Mock private FeedbackMessageRepository feedbackMessageRepository;
    @Mock private PlanRepository planRepository;
    @Mock private UserCreditBalanceRepository creditBalanceRepository;
    @Mock private CreditBalanceService creditBalanceService;
    @Mock private CreditPricingRuleRepository pricingRuleRepository;
    @Mock private CreditLedgerRepository creditLedgerRepository;
    @Mock private AdminStorageService adminStorageService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private FeedbackReportRealtimePublisher feedbackReportRealtimePublisher;
    @Mock private SecurityEventRepository securityEventRepository;
    @Mock private AuditService auditService;
    @Mock private OnlineUserHistoryService onlineUserHistoryService;
    @Mock private RefreshSessionService refreshSessionService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                settingsRepository,
                projectOwnershipRepository,
                projectUsageRepository,
                feedbackReportRepository,
                feedbackMessageRepository,
                planRepository,
                creditBalanceRepository,
                creditBalanceService,
                pricingRuleRepository,
                creditLedgerRepository,
                adminStorageService,
                passwordEncoder,
                feedbackReportRealtimePublisher,
                securityEventRepository,
                auditService,
                onlineUserHistoryService,
                refreshSessionService
        );
    }

    @Test
    @DisplayName("getOverview returns count metrics successfully")
    void getOverview_returnsMetrics() {
        when(userRepository.count()).thenReturn(10L);
        when(projectOwnershipRepository.count()).thenReturn(20L);
        when(feedbackReportRepository.count()).thenReturn(30L);
        when(feedbackReportRepository.countByStatus(FeedbackReportStatus.OPEN)).thenReturn(15L);
        when(settingsRepository.countByBlockedAtIsNotNull()).thenReturn(2L);
        AdminSeriesRow dailyUserGrowth = mock(AdminSeriesRow.class);
        when(dailyUserGrowth.getLabel()).thenReturn("2026-08-31");
        when(dailyUserGrowth.getValue()).thenReturn(2L);
        when(dailyUserGrowth.getPeriod()).thenReturn("day");
        when(userRepository.countGrowthByDay()).thenReturn(java.util.List.of(dailyUserGrowth));
        when(userRepository.countGrowthByMonth()).thenReturn(java.util.List.of());
        when(userRepository.countGrowthByQuarter()).thenReturn(java.util.List.of());
        when(userRepository.countGrowthByYear()).thenReturn(java.util.List.of());
        when(creditLedgerRepository.sumConsumptionByMonth()).thenReturn(java.util.List.of());
        when(creditLedgerRepository.sumConsumptionByDay()).thenReturn(java.util.List.of());
        when(creditLedgerRepository.sumConsumptionByQuarter()).thenReturn(java.util.List.of());
        when(creditLedgerRepository.sumConsumptionByYear()).thenReturn(java.util.List.of());
        when(settingsRepository.countUsersByPlan()).thenReturn(java.util.List.of());
        when(projectUsageRepository.findTopStorageUsers(5)).thenReturn(java.util.List.of());
        when(projectUsageRepository.findTopStorageProjects(5)).thenReturn(java.util.List.of());
        when(securityEventRepository.summarizeSince(any())).thenReturn(java.util.List.of());
        when(onlineUserHistoryService.recordAndSnapshot(anyLong(), any())).thenReturn(java.util.List.of(
                new AdminOverviewResponse.AdminSeriesPoint("2026-07-17T13:05:00Z", 0L, "minute")));
        when(adminStorageService.overview()).thenReturn(new AdminStorageOverviewResponse(
                0L,
                java.util.List.of(),
                new StorageUnknownResponse("database", "UNKNOWN", "unknown"),
                new StorageUnknownResponse("neo4j", "UNKNOWN", "unknown")));

        AdminOverviewResponse overview = adminService.getOverview();

        assertEquals(10L, overview.totalUsers());
        assertEquals(0L, overview.onlineUsers()); // JwtAuthFilter.getActiveUsersCount() returns 0 initially in unit test env
        assertEquals(20L, overview.totalProjects());
        assertEquals(30L, overview.totalReports());
        assertEquals(15L, overview.openReports());
        assertEquals(2L, overview.blockedUsers());
        assertEquals(1, overview.onlineUserHistory().size());
        assertTrue(overview.userGrowth().stream().anyMatch(point ->
                point.label().equals("2026-08-31") && point.value() == 2L && point.period().equals("day")));
        assertEquals("projects", overview.storage().sourceLabel());
        assertEquals(1, overview.securityAlerts().size());
        verify(userRepository, never()).findAll();
        verify(userRepository).countGrowthByDay();
        verify(settingsRepository, never()).findAll();
        verify(creditLedgerRepository, never()).findAll();
        verify(projectUsageRepository, never()).findAll();
        verify(projectOwnershipRepository, never()).findAll();
    }

    @Test
    @DisplayName("createUser throws EmailAlreadyExistsException if email exists")
    void createUser_emailExists_throwsException() {
        AdminCreateUserRequest req = new AdminCreateUserRequest("exists@test.local", "Display Name", "USER", "FREE", "password");
        when(userRepository.existsByEmailIgnoreCase("exists@test.local")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> adminService.createUser(req));
    }

    @Test
    @DisplayName("createUser creates user and settings without a non-canonical credit balance")
    void createUser_succeeds() {
        AdminCreateUserRequest req = new AdminCreateUserRequest("new@test.local", "New User", "USER", "FREE", "password");
        Plan plan = Plan.builder().code("FREE").storageLimitBytes(100L).monthlyCreditLimit(50).build();
        User savedUser = User.builder().id(UUID.randomUUID()).email("new@test.local").displayName("New User").role(Role.USER).build();

        when(userRepository.existsByEmailIgnoreCase("new@test.local")).thenReturn(false);
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(plan));
        when(passwordEncoder.encode("password")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AdminUserResponse response = adminService.createUser(req);

        assertEquals("new@test.local", response.email());
        assertEquals("USER", response.role());
        verify(userRepository).save(any(User.class));
        verify(settingsRepository).save(any(UserAccountSettings.class));
        verify(auditService).recordCurrentUser("USER_CREATE", savedUser.getId(), "USER", savedUser.getId().toString(),
                java.util.Map.of("email", savedUser.getEmail(), "role", savedUser.getRole().name(), "planCode", "FREE"));
        verifyNoInteractions(creditBalanceRepository);
    }

    @Test
    @DisplayName("updateQuota accepts an override equal to aggregate usage and stores bytes")
    void updateQuota_equalToActualUsage_storesBytes() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test")
                .role(Role.USER).build();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId).plan(Plan.builder().code("FREE").build()).build();
        AdminUserUpdateQuotaRequest request = new AdminUserUpdateQuotaRequest(2L, null);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(settingsRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(settings));
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        when(projectUsageRepository.sumStorageBytesByOwnerId(userId)).thenReturn(2_097_152L);

        adminService.updateQuota(userId, request);

        assertEquals(2_097_152L, settings.getStorageQuotaOverrideBytes());
        assertEquals(2_097_152L, user.getQuotaBytes());
        verify(settingsRepository).save(settings);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateQuota updates credit override through the atomic credit service")
    void updateQuota_creditOverride_usesAtomicCreditService() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test")
                .role(Role.USER).build();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId).plan(Plan.builder().code("FREE").build()).build();
        AdminUserUpdateQuotaRequest request = new AdminUserUpdateQuotaRequest(null, 750);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(settingsRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(settings));
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        adminService.updateQuota(userId, request);

        assertEquals(750, settings.getCreditQuotaOverride());
        verify(creditBalanceService).updateCurrentPeriodLimitSnapshot(userId, 750);
        verify(creditBalanceRepository, never()).save(any());
        verify(settingsRepository).save(settings);
    }

    @Test
    @DisplayName("adjustCredits delegates to atomic credit service")
    void adjustCredits_usesAtomicCreditService() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test")
                .role(Role.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        adminService.adjustCredits(userId, new com.vibegraph.auth.dto.AdminCreditAdjustmentRequest(25, "bonus"));

        verify(creditBalanceService).applyAdminAdjustment(userId, 25, "bonus");
        verify(creditBalanceRepository, never()).save(any());
        verify(creditLedgerRepository, never()).save(any());
        verify(auditService).recordCurrentUser("CREDIT_UPDATE", userId, "USER", userId.toString(),
                java.util.Map.of("creditsDelta", 25, "reasonProvided", true));
    }

    @Test
    @DisplayName("blockUser sets blocked fields correctly")
    void blockUser_succeeds() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder().userId(userId).build();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test").role(Role.USER).build();
        AdminUserBlockRequest req = new AdminUserBlockRequest("Spam", "Spam Policy violation");

        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AdminUserResponse response = adminService.blockUser(userId, req);

        assertTrue(response.blocked());
        assertEquals("Spam", response.blockedReason());
        assertEquals("Spam Policy violation", response.blockedReasonSafe());
        verify(settingsRepository).save(settings);
        verify(refreshSessionService).revokeAllForUser(userId, "ACCOUNT_BLOCKED");
        verify(auditService).recordCurrentUser("USER_BLOCK", userId, "USER", userId.toString(),
                java.util.Map.of("safeReason", "Spam Policy violation"));
    }

    @Test
    @DisplayName("deactivateUser deactivates user successfully")
    void deactivateUser_succeeds() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test").role(Role.USER).deactivated(false).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserResponse response = adminService.deactivateUser(
                userId, new com.vibegraph.auth.dto.AdminUserDeactivateRequest("private reason", "Account closed"));

        assertTrue(response.deactivated());
        verify(userRepository).save(user);
        verify(refreshSessionService).revokeAllForUser(userId, "ACCOUNT_DEACTIVATED");
        verify(auditService).recordCurrentUser("USER_DEACTIVATE", userId, "USER", userId.toString(),
                java.util.Map.of("safeReason", "Account closed"));
    }

    @Test
    @DisplayName("updateQuota rejects MiB override below actual project usage")
    void updateQuota_belowActualUsage_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test")
                .role(Role.USER).usedBytes(0L).build();
        UserAccountSettings settings = UserAccountSettings.builder().userId(userId).build();
        AdminUserUpdateQuotaRequest request = new AdminUserUpdateQuotaRequest(1L, null);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(settingsRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(settings));
        when(projectUsageRepository.sumStorageBytesByOwnerId(userId)).thenReturn(1_048_577L);

        assertThrows(QuotaBelowCurrentUsageException.class, () -> adminService.updateQuota(userId, request));
        verify(userRepository, never()).save(any(User.class));
        verify(settingsRepository, never()).save(any(UserAccountSettings.class));
    }

    @Test
    @DisplayName("getUsers delegates filtering and pagination to the repository")
    void getUsers_usesRepositoryPagingAndFilters() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("alice@test.local").displayName("Alice").role(Role.USER).build();
        var pageable = PageRequest.of(1, 3);
        // B-M4: the plan filter is validated against the plans table before the query runs.
        when(planRepository.existsByCode("PRO")).thenReturn(true);
        when(userRepository.findAllWithFilters("ali", "ACTIVE", "PRO", pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(user), pageable, 7));
        // H9: the page is enriched via two batch queries, not two queries per user.
        when(settingsRepository.findAllById(any()))
                .thenReturn(java.util.List.of(UserAccountSettings.builder()
                        .userId(userId).plan(Plan.builder().code("PRO").build()).build()));
        com.vibegraph.auth.repository.projection.StorageSum sum =
                mock(com.vibegraph.auth.repository.projection.StorageSum.class);
        when(sum.getOwnerId()).thenReturn(userId);
        when(sum.getTotal()).thenReturn(2048L);
        when(projectUsageRepository.sumStorageByOwners(any())).thenReturn(java.util.List.of(sum));

        var result = adminService.getUsers("ali", "ACTIVE", "PRO", pageable);

        assertEquals(7, result.getTotalElements());
        assertEquals("PRO", result.getContent().getFirst().planCode());
        verify(userRepository).findAllWithFilters("ali", "ACTIVE", "PRO", pageable);
        verify(userRepository, never()).findAll();
        verify(settingsRepository).findAllById(any());
        verify(projectUsageRepository).sumStorageByOwners(any());
        // No per-user N+1 lookups on the paged path.
        verify(settingsRepository, never()).findById(any());
        verify(projectUsageRepository, never()).sumStorageBytesByOwnerId(any());
    }

    @Test
    @DisplayName("getFeedbackReports delegates filtering and pagination to the repository")
    void getFeedbackReports_usesRepositoryPagingAndFilters() {
        FeedbackReport report = FeedbackReport.builder().id(UUID.randomUUID()).status(FeedbackReportStatus.OPEN)
                .title("Canvas issue").category(FeedbackCategory.BUG).build();
        var pageable = PageRequest.of(0, 5);
        when(feedbackReportRepository.findAllWithFilters(FeedbackReportStatus.OPEN, "canvas", pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(
                        report,
                        FeedbackReport.builder().id(UUID.randomUUID()).status(FeedbackReportStatus.OPEN)
                                .title("Canvas issue 2").category(FeedbackCategory.BUG).build(),
                        FeedbackReport.builder().id(UUID.randomUUID()).status(FeedbackReportStatus.OPEN)
                                .title("Canvas issue 3").category(FeedbackCategory.BUG).build(),
                        FeedbackReport.builder().id(UUID.randomUUID()).status(FeedbackReportStatus.OPEN)
                                .title("Canvas issue 4").category(FeedbackCategory.BUG).build()), pageable, 4));

        var result = adminService.getFeedbackReports("OPEN", "canvas", pageable);

        assertEquals(4, result.getTotalElements());
        verify(feedbackReportRepository).findAllWithFilters(FeedbackReportStatus.OPEN, "canvas", pageable);
        verify(feedbackReportRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("unblockUser clears blocked settings fields")
    void unblockUser_succeeds() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId).blockedAt(Instant.now()).blockedReason("Spam").blockedReasonSafe("Spam").build();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test").role(Role.USER).build();

        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AdminUserResponse response = adminService.unblockUser(userId);

        assertFalse(response.blocked());
        assertNull(response.blockedReason());
        verify(settingsRepository).save(settings);
    }

    @Test
    @DisplayName("updatePlan changes plan and storage limits")
    void updatePlan_succeeds() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder().userId(userId).build();
        Plan plan = Plan.builder().code("PRO").storageLimitBytes(2000L).monthlyCreditLimit(200).build();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test").role(Role.USER).quotaBytes(100L).build();

        when(settingsRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(settings));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        AdminUserResponse response = adminService.updatePlan(userId, new AdminUserUpdatePlanRequest("PRO"));

        assertEquals("PRO", response.planCode());
        assertEquals(0L, response.quotaMb());
        verify(settingsRepository).save(settings);
        verify(creditBalanceService).updateCurrentPeriodLimitSnapshot(userId, 200);
    }

    @Test
    @DisplayName("updatePlan preserves a credit override while changing the plan")
    void updatePlan_preservesCreditOverride() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId)
                .creditQuotaOverride(750)
                .build();
        Plan plan = Plan.builder().code("MAX").storageLimitBytes(4000L).monthlyCreditLimit(2000).build();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test")
                .role(Role.USER).quotaBytes(100L).build();

        when(settingsRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(settings));
        when(planRepository.findByCode("MAX")).thenReturn(Optional.of(plan));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        adminService.updatePlan(userId, new AdminUserUpdatePlanRequest("MAX"));

        verify(creditBalanceService).updateCurrentPeriodLimitSnapshot(userId, 750);
    }

    @Test
    @DisplayName("updateApiKeyCreationDisabled updates settings flag successfully")
    void updateApiKeyCreationDisabled_succeeds() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder().userId(userId).apiKeyCreationDisabled(false).build();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test").role(Role.USER).build();

        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AdminUserResponse response = adminService.updateApiKeyCreationDisabled(userId, true);

        assertTrue(response.apiKeyCreationDisabled());
        verify(settingsRepository).save(settings);
        verify(auditService).recordCurrentUser("API_KEY_CREATION_TOGGLE", userId, "USER", userId.toString(),
                java.util.Map.of("disabled", true, "previousDisabled", false));
    }

    @Test
    @DisplayName("closeFeedbackReport schedules deletion exactly 7 days after close")
    void closeReport_succeeds() {
        UUID reportId = UUID.randomUUID();
        FeedbackReport report = FeedbackReport.builder().id(reportId).status(FeedbackReportStatus.OPEN)
                .title("Bug").category(FeedbackCategory.BUG).build();

        when(feedbackReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        adminService.closeFeedbackReport(reportId);

        assertEquals(FeedbackReportStatus.CLOSED, report.getStatus());
        assertNotNull(report.getClosedAt());
        assertEquals(report.getClosedAt().plus(7, java.time.temporal.ChronoUnit.DAYS), report.getDeleteAfter());
        verify(feedbackReportRepository).save(report);
        verify(feedbackReportRealtimePublisher).publishReportClosed(argThat(response ->
                response.id().equals(reportId)
                        && response.status() == FeedbackReportStatus.CLOSED));
    }

    @Test
    @DisplayName("closeFeedbackReport preserves an existing closed report retention")
    void closeReport_alreadyClosed_isIdempotent() {
        UUID reportId = UUID.randomUUID();
        Instant closedAt = Instant.parse("2026-01-01T00:00:00Z");
        FeedbackReport report = FeedbackReport.builder().id(reportId).status(FeedbackReportStatus.CLOSED)
                .title("Bug").category(FeedbackCategory.BUG).closedAt(closedAt)
                .deleteAfter(closedAt.plus(7, java.time.temporal.ChronoUnit.DAYS)).build();
        when(feedbackReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        adminService.closeFeedbackReport(reportId);

        assertEquals(closedAt, report.getClosedAt());
        assertEquals(closedAt.plus(7, java.time.temporal.ChronoUnit.DAYS), report.getDeleteAfter());
        verify(feedbackReportRepository, never()).save(any(FeedbackReport.class));
        verify(feedbackReportRealtimePublisher, never()).publishReportClosed(any());
    }

    @Test
    @DisplayName("replyToFeedbackReport saves admin message and publishes realtime event")
    void replyToFeedbackReport_publishesRealtimeEvent() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        FeedbackReport report = FeedbackReport.builder().id(reportId).status(FeedbackReportStatus.OPEN)
                .title("Bug").category(FeedbackCategory.BUG).build();
        FeedbackMessage saved = FeedbackMessage.builder()
                .id(UUID.randomUUID())
                .reportId(reportId)
                .senderUserId(adminId)
                .senderRole(FeedbackSenderRole.ADMIN)
                .body("We are checking this now.")
                .build();

        when(feedbackReportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(feedbackMessageRepository.save(any(FeedbackMessage.class))).thenReturn(saved);

        adminService.replyToFeedbackReport(reportId, adminId, new AdminFeedbackReplyRequest("We are checking this now."));

        verify(feedbackMessageRepository).save(any(FeedbackMessage.class));
        verify(feedbackReportRealtimePublisher).publishMessageAdded(eq(reportId), argThat(message ->
                message.body().equals("We are checking this now.")
                        && message.senderRole() == FeedbackSenderRole.ADMIN));
    }

    // ── B-M4: admin user-list filter validation ────────────────────────────

    @Test
    @DisplayName("getUsers rejects a status outside the AccountStatus enum (B-M4)")
    void getUsers_rejectsUnknownStatusFilter() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adminService.getUsers(null, "SUSPENDED", null, PageRequest.of(0, 20)));
        assertEquals("Unsupported user status filter", ex.getMessage());
        verify(userRepository, never()).findAllWithFilters(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getUsers accepts enum statuses case-insensitively (B-M4)")
    void getUsers_acceptsEnumStatusCaseInsensitive() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAllWithFilters(null, "active", null, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        assertDoesNotThrow(() -> adminService.getUsers(null, "active", null, pageable));
    }

    @Test
    @DisplayName("getUsers rejects plan codes missing from the plans table (B-M4)")
    void getUsers_rejectsUnknownPlanFilter() {
        when(planRepository.existsByCode("LEGACY")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adminService.getUsers(null, null, "legacy", PageRequest.of(0, 20)));
        assertEquals("Unsupported plan filter", ex.getMessage());
        verify(userRepository, never()).findAllWithFilters(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getUsers accepts any plan present in the plans table — no hardcoded list (B-M4)")
    void getUsers_acceptsPlanFromDatabase() {
        Pageable pageable = PageRequest.of(0, 20);
        when(planRepository.existsByCode("STARTER")).thenReturn(true);
        when(userRepository.findAllWithFilters(null, null, "starter", pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        assertDoesNotThrow(() -> adminService.getUsers(null, null, "starter", pageable));
    }
}
