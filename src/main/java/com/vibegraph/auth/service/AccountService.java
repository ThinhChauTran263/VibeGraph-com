package com.vibegraph.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.User;
import com.vibegraph.auth.dto.AccountProfileUpdateRequest;
import com.vibegraph.auth.dto.AccountProjectPageRequest;
import com.vibegraph.auth.dto.AccountProjectResponse;
import com.vibegraph.auth.dto.AccountProjectsPageResponse;
import com.vibegraph.auth.dto.AccountUsageResponse;
import com.vibegraph.auth.dto.UserResponse;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.auth.repository.UserRepository;
import com.vibegraph.common.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final AccountSettingsService accountSettingsService;
    private final ProjectOwnershipRepository projectOwnershipRepository;

    @Transactional(readOnly = true)
    public UserResponse profile() {
        return UserResponse.from(currentUserEntity());
    }

    @Transactional
    public UserResponse updateProfile(AccountProfileUpdateRequest request) {
        User user = currentUserEntity();
        user.setDisplayName(request.displayName());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AccountUsageResponse usage() {
        UUID userId = currentUserEntity().getId();
        AccountQuotaSnapshot snapshot = accountSettingsService.quotaSnapshot(userId);
        return new AccountUsageResponse(
                snapshot.usedBytes(),
                snapshot.limitBytes(),
                snapshot.remainingBytes(),
                snapshot.planCode(),
                snapshot.planName(),
                snapshot.quotaOverrideBytes());
    }

    @Transactional(readOnly = true)
    public AccountProjectsPageResponse projects(AccountProjectPageRequest request) {
        UUID userId = currentUserEntity().getId();
        return AccountProjectsPageResponse.from(projectOwnershipRepository
                .findByOwnerId(userId, request.toPageable())
                .map(AccountProjectResponse::from));
    }

    private User currentUserEntity() {
        UUID userId = currentUser.id();
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
