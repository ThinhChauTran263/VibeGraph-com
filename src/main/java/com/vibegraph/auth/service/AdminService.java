package com.vibegraph.auth.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.CreditLedger;
import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.domain.FeedbackMessage;
import com.vibegraph.auth.domain.FeedbackReport;
import com.vibegraph.auth.domain.FeedbackReportStatus;
import com.vibegraph.auth.domain.FeedbackSenderRole;
import com.vibegraph.auth.domain.Plan;
import com.vibegraph.auth.domain.Role;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserAccountSettings;
import com.vibegraph.auth.domain.UserCreditBalance;
import com.vibegraph.auth.dto.AdminCreateUserRequest;
import com.vibegraph.auth.dto.AdminCreditAdjustmentRequest;
import com.vibegraph.auth.dto.AdminCreditOverviewResponse;
import com.vibegraph.auth.dto.AdminFeedbackDetailResponse;
import com.vibegraph.auth.dto.AdminFeedbackReplyRequest;
import com.vibegraph.auth.dto.AdminFeedbackResponse;
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
import com.vibegraph.common.exception.ForbiddenException;
import com.vibegraph.common.exception.QuotaBelowCurrentUsageException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserAccountSettingsRepository settingsRepository;
    private final ProjectOwnershipRepository projectOwnershipRepository;
    private final FeedbackReportRepository feedbackReportRepository;
    private final FeedbackMessageRepository feedbackMessageRepository;
    private final PlanRepository planRepository;
    private final UserCreditBalanceRepository creditBalanceRepository;
    private final CreditPricingRuleRepository pricingRuleRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        long totalUsers = userRepository.count();
        long onlineUsers = 5; // Polling/realtime mock
        long totalProjects = projectOwnershipRepository.count();
        long totalReports = feedbackReportRepository.count();
        long openReports = feedbackReportRepository.countByStatus(FeedbackReportStatus.OPEN);
        long blockedUsers = settingsRepository.countByBlockedAtIsNotNull();
        return new AdminOverviewResponse(
                totalUsers,
                onlineUsers,
                totalProjects,
                totalReports,
                openReports,
                blockedUsers,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String search, Pageable pageable) {
        Page<User> userPage;
        if (search != null && !search.isBlank()) {
            userPage = userRepository.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                    search, search, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        return userPage.map(this::toAdminUserResponse);
    }

    @Transactional
    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        Plan plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + request.planCode()));

        User user = User.builder()
                .email(email)
                .displayName(request.displayName())
                .role(Role.valueOf(request.role().toUpperCase()))
                .passwordHash(passwordEncoder.encode(request.temporaryPassword()))
                .quotaBytes(plan.getStorageLimitBytes())
                .build();
        User savedUser = userRepository.save(user);

        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(savedUser.getId())
                .plan(plan)
                .build();
        settingsRepository.save(settings);

        // Also create a default credit balance for the user
        LocalDate now = LocalDate.now();
        UserCreditBalance balance = UserCreditBalance.builder()
                .userId(savedUser.getId())
                .periodStart(now)
                .periodEnd(now.plusMonths(1))
                .creditsLimitSnapshot(plan.getMonthlyCreditLimit())
                .creditsUsed(0)
                .creditsAdjustment(0)
                .build();
        creditBalanceRepository.save(balance);

        return toAdminUserResponse(savedUser);
    }

    @Transactional
    public AdminUserResponse blockUser(UUID userId, AdminUserBlockRequest request) {
        UserAccountSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        settings.block(request.reason(), request.safeReason());
        settingsRepository.save(settings);
        return toAdminUserResponse(getUserOrThrow(userId));
    }

    @Transactional
    public AdminUserResponse unblockUser(UUID userId) {
        UserAccountSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        settings.setBlockedAt(null);
        settings.setBlockedReason(null);
        settings.setBlockedReasonSafe(null);
        settingsRepository.save(settings);
        return toAdminUserResponse(getUserOrThrow(userId));
    }

    @Transactional
    public AdminUserResponse deactivateUser(UUID userId) {
        User user = getUserOrThrow(userId);
        user.setDeactivated(true);
        userRepository.save(user);
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updatePlan(UUID userId, AdminUserUpdatePlanRequest request) {
        UserAccountSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        Plan plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + request.planCode()));
        settings.setPlan(plan);
        
        // Adjust standard quota to match the new plan limit (if not overridden)
        User user = getUserOrThrow(userId);
        if (settings.getStorageQuotaOverrideBytes() == null) {
            user.setQuotaBytes(plan.getStorageLimitBytes());
            userRepository.save(user);
        }
        
        settingsRepository.save(settings);
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateQuota(UUID userId, AdminUserUpdateQuotaRequest request) {
        User user = getUserOrThrow(userId);
        UserAccountSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));

        if (request.storageQuotaOverrideMb() != null) {
            long newQuotaBytes = request.storageQuotaOverrideMb() * 1024L * 1024L;
            if (newQuotaBytes < user.getUsedBytes()) {
                throw new QuotaBelowCurrentUsageException("Override quota cannot be less than current usage");
            }
            settings.setStorageQuotaOverrideBytes(newQuotaBytes);
            user.setQuotaBytes(newQuotaBytes);
            userRepository.save(user);
        }

        if (request.creditQuotaOverride() != null) {
            if (request.creditQuotaOverride() < 0) {
                throw new IllegalArgumentException("Credit override must be non-negative");
            }
            settings.setCreditQuotaOverride(request.creditQuotaOverride());
            // Adjust current period balance limit
            creditBalanceRepository.findFirstByUserIdOrderByPeriodEndDesc(userId)
                    .ifPresent(balance -> {
                        balance.setCreditsLimitSnapshot(request.creditQuotaOverride());
                        creditBalanceRepository.save(balance);
                    });
        }

        settingsRepository.save(settings);
        return toAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public AdminCreditOverviewResponse getCreditOverview(UUID userId) {
        // Ensure user exists
        getUserOrThrow(userId);

        UserCreditBalance balance = creditBalanceRepository.findFirstByUserIdOrderByPeriodEndDesc(userId)
                .orElseGet(() -> {
                    // Create one if it does not exist
                    UserAccountSettings settings = settingsRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
                    int limit = settings.getCreditQuotaOverride() != null 
                            ? settings.getCreditQuotaOverride() 
                            : settings.getPlan().getMonthlyCreditLimit();
                    LocalDate now = LocalDate.now();
                    UserCreditBalance newBal = UserCreditBalance.builder()
                            .userId(userId)
                            .periodStart(now)
                            .periodEnd(now.plusMonths(1))
                            .creditsLimitSnapshot(limit)
                            .creditsUsed(0)
                            .creditsAdjustment(0)
                            .build();
                    return creditBalanceRepository.save(newBal);
                });

        List<CreditLedger> ledgerHistory = creditLedgerRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        int creditBalance = balance.getCreditsLimitSnapshot() + balance.getCreditsAdjustment() - balance.getCreditsUsed();

        return new AdminCreditOverviewResponse(
                userId,
                balance.getCreditsLimitSnapshot(),
                balance.getCreditsUsed(),
                balance.getCreditsAdjustment(),
                creditBalance,
                ledgerHistory
        );
    }

    @Transactional
    public void adjustCredits(UUID userId, AdminCreditAdjustmentRequest request) {
        getUserOrThrow(userId);

        UserCreditBalance balance = creditBalanceRepository.findFirstByUserIdOrderByPeriodEndDesc(userId)
                .orElseThrow(() -> new IllegalArgumentException("Credit balance not found"));

        balance.setCreditsAdjustment(balance.getCreditsAdjustment() + request.creditsDelta());
        creditBalanceRepository.save(balance);

        CreditLedger ledger = CreditLedger.builder()
                .userId(userId)
                .balanceId(balance.getId())
                .source("ADMIN")
                .operationCode("ADMIN_ADJUSTMENT")
                .creditsDelta(request.creditsDelta())
                .metadata("{\"reason\":\"" + request.reason().replace("\"", "\\\"") + "\"}")
                .build();
        creditLedgerRepository.save(ledger);
    }

    @Transactional(readOnly = true)
    public List<CreditPricingRule> getPricingRules() {
        return pricingRuleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<AdminFeedbackResponse> getFeedbackReports() {
        return feedbackReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminFeedbackResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminFeedbackDetailResponse getFeedbackReportDetail(UUID reportId) {
        FeedbackReport report = feedbackReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback report not found"));
        List<FeedbackMessage> messages = feedbackMessageRepository.findByReportIdOrderByCreatedAtAsc(reportId);
        return new AdminFeedbackDetailResponse(toAdminFeedbackResponse(report), messages);
    }

    @Transactional
    public void replyToFeedbackReport(UUID reportId, UUID adminUserId, AdminFeedbackReplyRequest request) {
        FeedbackReport report = feedbackReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback report not found"));
        if (report.getStatus() == FeedbackReportStatus.CLOSED) {
            throw new IllegalStateException("Cannot reply to a closed report");
        }

        FeedbackMessage msg = FeedbackMessage.builder()
                .reportId(reportId)
                .senderUserId(adminUserId)
                .senderRole(FeedbackSenderRole.ADMIN)
                .body(request.body())
                .build();
        feedbackMessageRepository.save(msg);
    }

    @Transactional
    public void closeFeedbackReport(UUID reportId) {
        FeedbackReport report = feedbackReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback report not found"));
        report.setStatus(FeedbackReportStatus.CLOSED);
        report.setClosedAt(Instant.now());
        report.setDeleteAfter(Instant.now().plus(30, ChronoUnit.DAYS));
        feedbackReportRepository.save(report);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        UserAccountSettings settings = settingsRepository.findById(user.getId()).orElse(null);
        String planCode = (settings != null && settings.getPlan() != null) ? settings.getPlan().getCode() : null;
        Long storageQuotaOverrideBytes = settings != null ? settings.getStorageQuotaOverrideBytes() : null;
        Integer creditQuotaOverride = settings != null ? settings.getCreditQuotaOverride() : null;
        boolean blocked = settings != null && settings.isBlocked();
        String blockedReason = settings != null ? settings.getBlockedReason() : null;
        String blockedReasonSafe = settings != null ? settings.getBlockedReasonSafe() : null;

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isDeactivated(),
                blocked,
                blockedReason,
                blockedReasonSafe,
                planCode,
                storageQuotaOverrideBytes,
                creditQuotaOverride,
                user.getQuotaBytes(),
                user.getUsedBytes()
        );
    }

    private AdminFeedbackResponse toAdminFeedbackResponse(FeedbackReport report) {
        return new AdminFeedbackResponse(
                report.getId(),
                report.getUserId(),
                report.getStatus().name(),
                report.getCategory().name(),
                report.getTitle(),
                report.getCreatedAt(),
                report.getClosedAt(),
                report.getDeleteAfter()
        );
    }
}
