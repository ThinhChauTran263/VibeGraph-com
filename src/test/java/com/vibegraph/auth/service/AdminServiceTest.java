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
import com.vibegraph.auth.domain.FeedbackReport;
import com.vibegraph.auth.domain.FeedbackReportStatus;
import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.dto.AdminCreateUserRequest;
import com.vibegraph.auth.dto.AdminFeedbackReplyRequest;
import com.vibegraph.auth.dto.AdminOverviewResponse;
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
import com.vibegraph.common.exception.EmailAlreadyExistsException;
import com.vibegraph.common.exception.QuotaBelowCurrentUsageException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService")
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAccountSettingsRepository settingsRepository;
    @Mock private ProjectOwnershipRepository projectOwnershipRepository;
    @Mock private FeedbackReportRepository feedbackReportRepository;
    @Mock private FeedbackMessageRepository feedbackMessageRepository;
    @Mock private PlanRepository planRepository;
    @Mock private UserCreditBalanceRepository creditBalanceRepository;
    @Mock private CreditPricingRuleRepository pricingRuleRepository;
    @Mock private CreditLedgerRepository creditLedgerRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                userRepository,
                settingsRepository,
                projectOwnershipRepository,
                feedbackReportRepository,
                feedbackMessageRepository,
                planRepository,
                creditBalanceRepository,
                pricingRuleRepository,
                creditLedgerRepository,
                passwordEncoder
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

        AdminOverviewResponse overview = adminService.getOverview();

        assertEquals(10L, overview.totalUsers());
        assertEquals(5L, overview.onlineUsers());
        assertEquals(20L, overview.totalProjects());
        assertEquals(30L, overview.totalReports());
        assertEquals(15L, overview.openReports());
        assertEquals(2L, overview.blockedUsers());
    }

    @Test
    @DisplayName("createUser throws EmailAlreadyExistsException if email exists")
    void createUser_emailExists_throwsException() {
        AdminCreateUserRequest req = new AdminCreateUserRequest("exists@test.local", "Display Name", "USER", "FREE", "password");
        when(userRepository.existsByEmailIgnoreCase("exists@test.local")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> adminService.createUser(req));
    }

    @Test
    @DisplayName("createUser creates user and maps plan and credit default balance")
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
        verify(creditBalanceRepository).save(any());
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
    }

    @Test
    @DisplayName("deactivateUser deactivates user successfully")
    void deactivateUser_succeeds() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test").role(Role.USER).deactivated(false).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserResponse response = adminService.deactivateUser(userId);

        assertTrue(response.deactivated());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateQuota throws exception if storageQuotaOverride is below current usage")
    void updateQuota_belowUsage_throwsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.local").displayName("Test").role(Role.USER).usedBytes(5000L).build();
        UserAccountSettings settings = UserAccountSettings.builder().userId(userId).build();
        AdminUserUpdateQuotaRequest req = new AdminUserUpdateQuotaRequest(4L, null); // 4MB = 4194304 Bytes < 5000L? Wait, 4MB is 4194304 which is larger. Let's make override 0MB = 0 Bytes < 5000L.

        AdminUserUpdateQuotaRequest reqBelow = new AdminUserUpdateQuotaRequest(0L, null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        assertThrows(QuotaBelowCurrentUsageException.class, () -> adminService.updateQuota(userId, reqBelow));
    }

    @Test
    @DisplayName("closeFeedbackReport closes report and schedules deletion in 30 days")
    void closeReport_succeeds() {
        UUID reportId = UUID.randomUUID();
        FeedbackReport report = FeedbackReport.builder().id(reportId).status(FeedbackReportStatus.OPEN).title("Bug").category(FeedbackCategory.BUG).build();

        when(feedbackReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        adminService.closeFeedbackReport(reportId);

        assertEquals(FeedbackReportStatus.CLOSED, report.getStatus());
        assertNotNull(report.getClosedAt());
        assertNotNull(report.getDeleteAfter());
        verify(feedbackReportRepository).save(report);
    }
}
