package com.vibegraph.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.domain.AccountStatus;
import com.vibegraph.auth.domain.CreditLedger;
import com.vibegraph.auth.domain.CreditPricingRule;
import com.vibegraph.auth.domain.FeedbackCategory;
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
import com.vibegraph.auth.dto.AdminOverviewResponse.AdminDistributionPoint;
import com.vibegraph.auth.dto.AdminOverviewResponse.AdminSecurityAlert;
import com.vibegraph.auth.dto.AdminOverviewResponse.AdminSeriesPoint;
import com.vibegraph.auth.dto.AdminOverviewResponse.AdminStorageSubject;
import com.vibegraph.auth.dto.AdminOverviewResponse.AdminStorageSummary;
import com.vibegraph.auth.dto.AdminPlanResponse;
import com.vibegraph.auth.dto.AdminPricingRuleResponse;
import com.vibegraph.auth.dto.AdminStorageOverviewResponse;
import com.vibegraph.auth.dto.AdminUserBlockRequest;
import com.vibegraph.auth.dto.AdminUserDeactivateRequest;
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
import com.vibegraph.auth.repository.projection.AdminDistributionRow;
import com.vibegraph.auth.repository.projection.AdminSecurityAlertRow;
import com.vibegraph.auth.repository.projection.AdminSeriesRow;
import com.vibegraph.auth.repository.projection.AdminStorageSubjectRow;
import com.vibegraph.auth.repository.projection.StorageSum;
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
    private final com.vibegraph.auth.repository.ProjectUsageRepository projectUsageRepository;
    private final FeedbackReportRepository feedbackReportRepository;
    private final FeedbackMessageRepository feedbackMessageRepository;
    private final PlanRepository planRepository;
    private final UserCreditBalanceRepository creditBalanceRepository;
    private final CreditBalanceService creditBalanceService;
    private final CreditPricingRuleRepository pricingRuleRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final AdminStorageService adminStorageService;
    private final PasswordEncoder passwordEncoder;
    private final FeedbackReportRealtimePublisher feedbackReportRealtimePublisher;
    private final SecurityEventRepository securityEventRepository;
    private final AuditService auditService;
    private final OnlineUserHistoryService onlineUserHistoryService;
    private final RefreshSessionService refreshSessionService;

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        long totalUsers = userRepository.count();
        long onlineUsers = com.vibegraph.auth.web.JwtAuthFilter.getActiveUsersCount();
        long totalProjects = projectOwnershipRepository.count();
        long totalReports = feedbackReportRepository.count();
        long openReports = feedbackReportRepository.countByStatus(FeedbackReportStatus.OPEN);
        long blockedUsers = settingsRepository.countByBlockedAtIsNotNull();
        Instant now = Instant.now();
        List<AdminSeriesPoint> onlineUserHistory =
                onlineUserHistoryService.recordAndSnapshot(onlineUsers, now);
        return new AdminOverviewResponse(
                totalUsers,
                onlineUsers,
                totalProjects,
                totalReports,
                openReports,
                blockedUsers,
                now,
                buildUserGrowth(),
                buildCreditConsumption(),
                buildStorageSummary(),
                buildPlanDistribution(),
                buildTopStorageUsers(),
                buildTopStorageProjects(),
                buildSecurityAlerts(blockedUsers, now),
                onlineUserHistory
        );
    }

    private List<AdminSeriesPoint> buildUserGrowth() {
        return mergeSeriesRows(
                List.of(),
                userRepository.countGrowthByMonth(),
                userRepository.countGrowthByQuarter(),
                userRepository.countGrowthByYear());
    }

    private List<AdminSeriesPoint> buildCreditConsumption() {
        return mergeSeriesRows(
                creditLedgerRepository.sumConsumptionByDay(),
                creditLedgerRepository.sumConsumptionByMonth(),
                creditLedgerRepository.sumConsumptionByQuarter(),
                creditLedgerRepository.sumConsumptionByYear());
    }

    private List<AdminSeriesPoint> mergeSeriesRows(
            List<AdminSeriesRow> daily,
            List<AdminSeriesRow> monthly,
            List<AdminSeriesRow> quarterly,
            List<AdminSeriesRow> yearly) {
        List<AdminSeriesPoint> points = new ArrayList<>();
        appendSeries(points, daily);
        appendSeries(points, monthly);
        appendSeries(points, quarterly);
        appendSeries(points, yearly);
        return points;
    }

    private void appendSeries(List<AdminSeriesPoint> points, List<AdminSeriesRow> rows) {
        if (rows != null) {
            rows.forEach(row -> points.add(toSeriesPoint(row)));
        }
    }

    private AdminSeriesPoint toSeriesPoint(AdminSeriesRow row) {
        return new AdminSeriesPoint(row.getLabel(), row.getValue() != null ? row.getValue() : 0L, row.getPeriod());
    }

    private AdminStorageSummary buildStorageSummary() {
        AdminStorageOverviewResponse overview = adminStorageService.overview();
        return overview.mounts().stream()
                .filter(mount -> mount.available() && mount.totalBytes() != null && mount.totalBytes() > 0)
                .findFirst()
                .map(mount -> new AdminStorageSummary(
                        mount.usedBytes() != null ? mount.usedBytes() : overview.trackedProjectUsageBytes(),
                        mount.totalBytes(),
                        mount.label(),
                        null))
                .orElse(new AdminStorageSummary(overview.trackedProjectUsageBytes(), 0L, "projects", null));
    }

    private List<AdminDistributionPoint> buildPlanDistribution() {
        return settingsRepository.countUsersByPlan().stream()
                .map(this::toDistributionPoint)
                .toList();
    }

    private AdminDistributionPoint toDistributionPoint(AdminDistributionRow row) {
        return new AdminDistributionPoint(row.getLabel(), row.getValue() != null ? row.getValue() : 0L);
    }

    private List<AdminStorageSubject> buildTopStorageUsers() {
        return projectUsageRepository.findTopStorageUsers(5).stream()
                .map(this::toStorageSubject)
                .toList();
    }

    private List<AdminStorageSubject> buildTopStorageProjects() {
        return projectUsageRepository.findTopStorageProjects(5).stream()
                .map(this::toStorageSubject)
                .toList();
    }

    private AdminStorageSubject toStorageSubject(AdminStorageSubjectRow row) {
        return new AdminStorageSubject(
                row.getId(),
                row.getName(),
                row.getOwnerEmail(),
                row.getUsedBytes() != null ? row.getUsedBytes() : 0L);
    }

    private List<AdminSecurityAlert> buildSecurityAlerts(long blockedUsers, Instant now) {
        List<AdminSecurityAlert> alerts = new ArrayList<>();
        if (blockedUsers > 0) {
            alerts.add(new AdminSecurityAlert(
                    "blocked-users", "ACCOUNT_BLOCK", "WARNING",
                    blockedUsers + " blocked account(s) require review", now));
        }
        java.util.Optional.ofNullable(securityEventRepository.summarizeSince(now.minus(24, ChronoUnit.HOURS)))
                .orElseGet(List::of).stream()
                .map(this::toSecurityAlert)
                .forEach(alerts::add);
        return alerts;
    }

    private AdminSecurityAlert toSecurityAlert(AdminSecurityAlertRow row) {
        long value = row.getValue() == null ? 0L : row.getValue();
        return new AdminSecurityAlert(
                "security-" + row.getType().toLowerCase() + "-" + row.getSeverity().toLowerCase(),
                row.getType(), row.getSeverity(), value + " event(s) in the last 24 hours", row.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String search, String status, String plan, Pageable pageable) {
        validateUserStatus(status);
        validatePlanCode(plan);
        Page<User> page = userRepository.findAllWithFilters(search, status, plan, pageable);
        if (page.isEmpty()) {
            return page.map(this::toAdminUserResponse);
        }
        // H9: two batch queries for the whole page instead of 2 per user (settings + storage SUM).
        List<UUID> ids = page.getContent().stream().map(User::getId).toList();
        Map<UUID, UserAccountSettings> settingsById = settingsRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserAccountSettings::getUserId, Function.identity()));
        Map<UUID, Long> storageById = projectUsageRepository.sumStorageByOwners(ids).stream()
                .collect(Collectors.toMap(StorageSum::getOwnerId, StorageSum::getTotal));
        return page.map(user -> toAdminUserResponse(user,
                settingsById.get(user.getId()),
                storageById.getOrDefault(user.getId(), 0L)));
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
        auditService.recordCurrentUser("USER_CREATE", savedUser.getId(), "USER", savedUser.getId().toString(),
                details("email", savedUser.getEmail(), "role", savedUser.getRole().name(),
                        "planCode", request.planCode()));

        return toAdminUserResponse(savedUser);
    }

    @Transactional
    public AdminUserResponse blockUser(UUID userId, AdminUserBlockRequest request) {
        UserAccountSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        settings.block(request.reason(), request.safeReason());
        settingsRepository.save(settings);
        refreshSessionService.revokeAllForUser(userId, "ACCOUNT_BLOCKED");
        auditService.recordCurrentUser("USER_BLOCK", userId, "USER", userId.toString(),
                details("safeReason", request.safeReason()));
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
        auditService.recordCurrentUser("USER_UNBLOCK", userId, "USER", userId.toString(), java.util.Map.of());
        return toAdminUserResponse(getUserOrThrow(userId));
    }

    @Transactional
    public AdminUserResponse deactivateUser(UUID userId, AdminUserDeactivateRequest request) {
        User user = getUserOrThrow(userId);
        user.setDeactivated(true);
        user.setDeactivatedAt(Instant.now());
        user.setDeactivationReason(request.reason());
        user.setDeactivationReasonSafe(request.safeReason());
        userRepository.save(user);
        refreshSessionService.revokeAllForUser(userId, "ACCOUNT_DEACTIVATED");
        auditService.recordCurrentUser("USER_DEACTIVATE", userId, "USER", userId.toString(),
                details("safeReason", request.safeReason()));
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updatePlan(UUID userId, AdminUserUpdatePlanRequest request) {
        UserAccountSettings settings = settingsRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        Plan plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + request.planCode()));
        String previousPlanCode = settings.getPlan() == null ? null : settings.getPlan().getCode();
        settings.setPlan(plan);

        // Adjust standard quota to match the new plan limit (if not overridden)
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (settings.getStorageQuotaOverrideBytes() == null) {
            user.setQuotaBytes(plan.getStorageLimitBytes());
            userRepository.save(user);
        }

        settingsRepository.save(settings);
        int effectiveCreditLimit = settings.getCreditQuotaOverride() != null
                ? settings.getCreditQuotaOverride()
                : plan.getMonthlyCreditLimit();
        creditBalanceService.updateCurrentPeriodLimitSnapshot(userId, effectiveCreditLimit);
        auditService.recordCurrentUser("PLAN_UPDATE", userId, "USER", userId.toString(),
                details("previousPlanCode", previousPlanCode, "planCode", request.planCode(),
                        "storageQuotaBytes", user.getQuotaBytes()));
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateQuota(UUID userId, AdminUserUpdateQuotaRequest request) {
        UserAccountSettings settings = settingsRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (request.storageQuotaOverrideMb() != null) {
            long newQuotaBytes;
            try {
                newQuotaBytes = StorageUnitConverter.mbToBytes(request.storageQuotaOverrideMb());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Storage quota override is too large");
            }
            long actualUsageBytes = projectUsageRepository.sumStorageBytesByOwnerId(userId);
            if (newQuotaBytes < actualUsageBytes) {
                throw new QuotaBelowCurrentUsageException(actualUsageBytes, newQuotaBytes);
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
            creditBalanceService.updateCurrentPeriodLimitSnapshot(userId, request.creditQuotaOverride());
        }

        settingsRepository.save(settings);
        auditService.recordCurrentUser("QUOTA_UPDATE", userId, "USER", userId.toString(),
                details("storageQuotaOverrideMb", request.storageQuotaOverrideMb(),
                        "creditQuotaOverride", request.creditQuotaOverride()));
        return toAdminUserResponse(user);
    }

    @Transactional
    public AdminCreditOverviewResponse getCreditOverview(UUID userId) {
        getUserOrThrow(userId);
        UserCreditBalance balance = creditBalanceService.findOrCreateCurrentPeriod(userId);

        List<CreditLedger> ledgerHistory = creditLedgerRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 100));

        long creditBalance = (long) balance.getCreditsLimitSnapshot()
                + balance.getCreditsAdjustment()
                - balance.getCreditsUsed();

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
        creditBalanceService.applyAdminAdjustment(userId, request.creditsDelta(), request.reason());
        auditService.recordCurrentUser("CREDIT_UPDATE", userId, "USER", userId.toString(),
                java.util.Map.of("creditsDelta", request.creditsDelta(), "reasonProvided", true));
    }

    @Transactional(readOnly = true)
    public List<AdminPricingRuleResponse> getPricingRules() {
        return pricingRuleRepository.findAll().stream()
                .map(AdminPricingRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPlanResponse> getPlans() {
        return planRepository.findAll().stream()
                .map(AdminPlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserDetail(UUID userId) {
        return toAdminUserResponse(getUserOrThrow(userId));
    }

    @Transactional
    public AdminUserResponse updateApiKeyCreationDisabled(UUID userId, boolean disabled) {
        UserAccountSettings settings = settingsRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        boolean previousDisabled = settings.isApiKeyCreationDisabled();
        settings.setApiKeyCreationDisabled(disabled);
        settingsRepository.save(settings);
        auditService.recordCurrentUser("API_KEY_CREATION_TOGGLE", userId, "USER", userId.toString(),
                java.util.Map.of("disabled", disabled, "previousDisabled", previousDisabled));
        return toAdminUserResponse(getUserOrThrow(userId));
    }

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public Page<AdminFeedbackResponse> getFeedbackReports(String status, String q, Pageable pageable) {
        FeedbackReportStatus parsedStatus = parseFeedbackStatus(status);
        return feedbackReportRepository.findAllWithFilters(parsedStatus, q, pageable)
                .map(this::toAdminFeedbackResponse);
    }

    @Transactional(transactionManager = "supabaseTransactionManager", readOnly = true)
    public AdminFeedbackDetailResponse getFeedbackReportDetail(UUID reportId) {
        FeedbackReport report = feedbackReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback report not found"));
        List<FeedbackMessage> messages = feedbackMessageRepository.findByReportIdOrderByCreatedAtAsc(reportId);
        return new AdminFeedbackDetailResponse(
                toAdminFeedbackResponse(report), messages.stream().map(this::toMessageResponse).toList());
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
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
        FeedbackMessage saved = feedbackMessageRepository.save(msg);
        feedbackReportRealtimePublisher.publishMessageAdded(
                reportId,
                new com.vibegraph.auth.dto.FeedbackMessageResponse(
                        saved.getId(),
                        saved.getSenderRole(),
                        saved.getBody(),
                        saved.getCreatedAt()));
        auditService.recordCurrentUser("REPORT_ADMIN_REPLY", report.getUserId(), "REPORT", reportId.toString(),
                java.util.Map.of("messageId", saved.getId().toString()));
    }

    @Transactional(transactionManager = "supabaseTransactionManager")
    public void closeFeedbackReport(UUID reportId) {
        FeedbackReport report = feedbackReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback report not found"));
        if (report.getStatus() == FeedbackReportStatus.CLOSED) {
            return;
        }
        Instant closedAt = Instant.now();
        report.setStatus(FeedbackReportStatus.CLOSED);
        report.setClosedAt(closedAt);
        report.setDeleteAfter(closedAt.plus(7, ChronoUnit.DAYS));
        feedbackReportRepository.save(report);
        feedbackReportRealtimePublisher.publishReportClosed(toReportResponse(report));
        auditService.recordCurrentUser("REPORT_CLOSE", report.getUserId(), "REPORT", reportId.toString(),
                java.util.Map.of("deleteAfter", report.getDeleteAfter().toString()));
    }

    private void validateUserStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        // B-M4: validate against the AccountStatus enum instead of a hardcoded list, so the
        // filter vocabulary cannot drift from the statuses UserResponse actually emits.
        if (AccountStatus.fromString(status).isEmpty()) {
            throw new IllegalArgumentException("Unsupported user status filter");
        }
    }

    private void validatePlanCode(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return;
        }
        // B-M4: validate against the plans table instead of a hardcoded list — adding a new
        // plan in the DB requires no code change here, and unknown codes are rejected.
        if (!planRepository.existsByCode(planCode.trim().toUpperCase())) {
            throw new IllegalArgumentException("Unsupported plan filter");
        }
    }

    private FeedbackReportStatus parseFeedbackStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return FeedbackReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported feedback report status");
        }
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        // Single-user paths (block/unban/plan updates, detail view): per-user lookups are fine;
        // the paged listing uses the batch overload below (H9).
        UserAccountSettings settings = settingsRepository.findById(user.getId()).orElse(null);
        long sourceUsageBytes = projectUsageRepository.sumStorageBytesByOwnerId(user.getId());
        return toAdminUserResponse(user, settings, sourceUsageBytes);
    }

    private AdminUserResponse toAdminUserResponse(User user, UserAccountSettings settings, long sourceUsageBytes) {
        String planCode = (settings != null && settings.getPlan() != null) ? settings.getPlan().getCode() : null;
        Long storageQuotaOverrideBytes = settings != null ? settings.getStorageQuotaOverrideBytes() : null;
        Integer creditQuotaOverride = settings != null ? settings.getCreditQuotaOverride() : null;
        boolean blocked = settings != null && settings.isBlocked();
        String blockedReason = settings != null ? settings.getBlockedReason() : null;
        String blockedReasonSafe = settings != null ? settings.getBlockedReasonSafe() : null;
        boolean apiKeyCreationDisabled = settings != null && settings.isApiKeyCreationDisabled();
        long effectiveQuotaBytes = settings != null && settings.getPlan() != null
                ? AccountSettingsService.effectiveLimitBytes(settings)
                : user.getQuotaBytes();

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isDeactivated(),
                user.getDeactivationReason(),
                user.getDeactivationReasonSafe(),
                blocked,
                blockedReason,
                blockedReasonSafe,
                planCode,
                storageQuotaOverrideBytes == null
                        ? null
                        : StorageUnitConverter.bytesToAvailableMb(storageQuotaOverrideBytes),
                creditQuotaOverride,
                StorageUnitConverter.bytesToAvailableMb(effectiveQuotaBytes),
                StorageUnitConverter.bytesToUsedMb(sourceUsageBytes),
                apiKeyCreationDisabled
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

    private com.vibegraph.auth.dto.FeedbackReportResponse toReportResponse(FeedbackReport report) {
        return new com.vibegraph.auth.dto.FeedbackReportResponse(
                report.getId(),
                report.getStatus(),
                report.getCategory(),
                report.getTitle(),
                report.getCreatedAt(),
                report.getClosedAt(),
                report.getDeleteAfter()
        );
    }

    private com.vibegraph.auth.dto.FeedbackMessageResponse toMessageResponse(FeedbackMessage message) {
        return new com.vibegraph.auth.dto.FeedbackMessageResponse(
                message.getId(), message.getSenderRole(), message.getBody(), message.getCreatedAt());
    }

    private java.util.Map<String, Object> details(Object... entries) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            details.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return details;
    }
}
