package com.vibegraph.auth.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.domain.UserCreditBalance;
import com.vibegraph.auth.dto.AccountCreditLedgerResponse;
import com.vibegraph.auth.dto.AccountProfileUpdateRequest;
import com.vibegraph.auth.dto.AccountPasswordChangeRequest;
import com.vibegraph.auth.dto.AccountProjectPageRequest;
import com.vibegraph.auth.dto.AccountProjectResponse;
import com.vibegraph.auth.dto.AccountProjectsPageResponse;
import com.vibegraph.auth.dto.AccountSessionStateResponse;
import com.vibegraph.auth.dto.AccountUsageResponse;
import com.vibegraph.auth.dto.FeatureCapability;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.CreditLedgerRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.UnauthorizedException;
import com.vibegraph.common.exception.InvalidCredentialsException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;
    private final CreditBalanceService creditBalanceService;
    private final CreditLedgerRepository creditLedgerRepository;
    private final ProjectOwnershipRepository projectOwnershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final FeatureGateService featureGateService;

    @Transactional(readOnly = true)
    public UserResponse profile() {
        User user = currentUserEntity();
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public AccountSessionStateResponse sessionState() {
        UserResponse user = toUserResponse(currentUserEntity());
        Map<String, FeatureCapability> features = featureGateService.capabilities();
        if (!"ACTIVE".equals(user.accountStatus())) {
            features = restrictProductCapabilities(features, user.safeReason());
        }
        return AccountSessionStateResponse.from(user, features);
    }

    private Map<String, FeatureCapability> restrictProductCapabilities(
            Map<String, FeatureCapability> features,
            String reason) {
        Map<String, FeatureCapability> restricted = new LinkedHashMap<>(features);
        restricted.replaceAll((key, capability) -> FeatureGateService.REGISTRATION.equals(key)
                ? capability
                : FeatureCapability.deny(
                        reason == null || reason.isBlank() ? "Account access is restricted" : reason));
        return Map.copyOf(restricted);
    }

    @Transactional
    public UserResponse updateProfile(AccountProfileUpdateRequest request) {
        User user = currentUserEntity();
        user.setDisplayName(request.displayName());
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(AccountPasswordChangeRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }
        User user = currentUserEntity();
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public AccountUsageResponse usage() {
        UUID userId = currentUserEntity().getId();
        AccountQuotaSnapshot snapshot = accountSettingsService.quotaSnapshot(userId);
        UserCreditBalance balance = creditBalanceService.findOrCreateCurrentPeriod(userId);
        long creditsLimit = Math.max(0L,
                (long) balance.getCreditsLimitSnapshot() + balance.getCreditsAdjustment());
        long creditsUsed = balance.getCreditsUsed();
        Long quotaOverrideMb = snapshot.quotaOverrideBytes() == null
                ? null
                : StorageUnitConverter.bytesToAvailableMb(snapshot.quotaOverrideBytes());
        return new AccountUsageResponse(
                StorageUnitConverter.bytesToUsedMb(snapshot.usedBytes()),
                StorageUnitConverter.bytesToAvailableMb(snapshot.limitBytes()),
                StorageUnitConverter.bytesToAvailableMb(snapshot.remainingBytes()),
                snapshot.planCode(),
                snapshot.planName(),
                quotaOverrideMb,
                creditsUsed,
                creditsLimit,
                Math.max(0, creditsLimit - creditsUsed));
    }

    @Transactional(readOnly = true)
    public java.util.List<AccountCreditLedgerResponse> creditLedger(int limit) {
        UUID userId = currentUserEntity().getId();
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        return creditLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, boundedLimit))
                .stream()
                .map(AccountCreditLedgerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountProjectsPageResponse projects(AccountProjectPageRequest request) {
        UUID userId = currentUserEntity().getId();
        return AccountProjectsPageResponse.from(projectOwnershipRepository
                .findByOwnerId(userId, request.toPageable())
                .map(AccountProjectResponse::from));
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.from(user, accountSettingsService.findSettings(user.getId()));
    }

    private User currentUserEntity() {
        UUID userId = currentUser.id();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
