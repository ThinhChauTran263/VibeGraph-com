package com.vibegraph.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.FeedbackReportStatus;
import com.vibegraph.auth.dto.AdminStorageOverviewResponse;
import com.vibegraph.auth.dto.StorageUnknownResponse;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.CreditPricingRuleRepository;
import com.vibegraph.auth.repository.FeedbackMessageRepository;
import com.vibegraph.auth.repository.FeedbackReportRepository;
import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.auth.repository.SecurityEventRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;
import com.vibegraph.auth.repository.UserCreditBalanceRepository;
import com.vibegraph.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Admin overview aggregate queries")
class AdminOverviewAggregateTest {

    @Mock UserRepository userRepository;
    @Mock UserAccountSettingsRepository settingsRepository;
    @Mock ProjectOwnershipRepository projectOwnershipRepository;
    @Mock ProjectUsageRepository projectUsageRepository;
    @Mock FeedbackReportRepository feedbackReportRepository;
    @Mock FeedbackMessageRepository feedbackMessageRepository;
    @Mock PlanRepository planRepository;
    @Mock UserCreditBalanceRepository creditBalanceRepository;
    @Mock CreditBalanceService creditBalanceService;
    @Mock CreditPricingRuleRepository pricingRuleRepository;
    @Mock CreditLedgerRepository creditLedgerRepository;
    @Mock AdminStorageService adminStorageService;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock FeedbackReportRealtimePublisher feedbackReportRealtimePublisher;
    @Mock SecurityEventRepository securityEventRepository;
    @Mock AuditService auditService;
    @Mock OnlineUserHistoryService onlineUserHistoryService;

    @InjectMocks AdminService adminService;

    @Test
    @DisplayName("overview uses bounded aggregate and top-N repository operations")
    void getOverview_neverLoadsLargeTables() {
        when(userRepository.count()).thenReturn(10L);
        when(projectOwnershipRepository.count()).thenReturn(20L);
        when(feedbackReportRepository.count()).thenReturn(3L);
        when(feedbackReportRepository.countByStatus(FeedbackReportStatus.OPEN)).thenReturn(1L);
        when(settingsRepository.countByBlockedAtIsNotNull()).thenReturn(0L);
        when(userRepository.countGrowthByDay()).thenReturn(List.of());
        when(userRepository.countGrowthByMonth()).thenReturn(List.of());
        when(userRepository.countGrowthByQuarter()).thenReturn(List.of());
        when(userRepository.countGrowthByYear()).thenReturn(List.of());
        when(creditLedgerRepository.sumConsumptionByDay()).thenReturn(List.of());
        when(creditLedgerRepository.sumConsumptionByMonth()).thenReturn(List.of());
        when(creditLedgerRepository.sumConsumptionByQuarter()).thenReturn(List.of());
        when(creditLedgerRepository.sumConsumptionByYear()).thenReturn(List.of());
        when(settingsRepository.countUsersByPlan()).thenReturn(List.of());
        when(projectUsageRepository.findTopStorageUsers(5)).thenReturn(List.of());
        when(projectUsageRepository.findTopStorageProjects(5)).thenReturn(List.of());
        when(securityEventRepository.summarizeSince(any())).thenReturn(List.of());
        when(onlineUserHistoryService.recordAndSnapshot(anyLong(), any())).thenReturn(List.of());
        when(adminStorageService.overview()).thenReturn(new AdminStorageOverviewResponse(
                0L, List.of(),
                new StorageUnknownResponse("database", "UNKNOWN", "unknown"),
                new StorageUnknownResponse("neo4j", "UNKNOWN", "unknown")));

        var overview = adminService.getOverview();

        assertThat(overview.totalUsers()).isEqualTo(10L);
        assertThat(overview.totalProjects()).isEqualTo(20L);
        verify(projectUsageRepository).findTopStorageUsers(5);
        verify(projectUsageRepository).findTopStorageProjects(5);
        verify(userRepository, never()).findAll();
        verify(projectOwnershipRepository, never()).findAll();
        verify(projectUsageRepository, never()).findAll();
        verify(creditLedgerRepository, never()).findAll();
    }
}
