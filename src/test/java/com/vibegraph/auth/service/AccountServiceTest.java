package com.vibegraph.auth.service;

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

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectOwnershipStatus;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.domain.Role;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account service")
class AccountServiceTest {

    @Mock
    private CurrentUser currentUser;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountSettingsService accountSettingsService;

    @Mock
    private ProjectOwnershipRepository projectOwnershipRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                currentUser,
                userRepository,
                accountSettingsService,
                projectOwnershipRepository);
    }

    @Test
    @DisplayName("profile returns the current authenticated user only")
    void profile_currentUser_returnsSafeProjection() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("me@test.local")
                .displayName("Me")
                .role(Role.USER)
                .passwordHash("hash")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse profile = accountService.profile();

        assertEquals(userId.toString(), profile.id());
        assertEquals("me@test.local", profile.email());
        assertEquals("Me", profile.displayName());
        assertEquals("USER", profile.role());
    }

    @Test
    @DisplayName("profile throws unauthorized when the authenticated user no longer exists")
    void profile_missingCurrentUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> accountService.profile());
    }

    @Test
    @DisplayName("updateProfile updates displayName only")
    void updateProfile_displayNameOnly_preservesEmailAndRole() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("original@test.local")
                .displayName("Old")
                .role(Role.ADMIN)
                .passwordHash("hash")
                .build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse profile = accountService.updateProfile(new AccountProfileUpdateRequest("  New Name  "));

        assertEquals("New Name", profile.displayName());
        assertEquals("original@test.local", profile.email());
        assertEquals("ADMIN", profile.role());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("usage returns the current user's quota snapshot")
    void usage_currentUser_returnsQuotaSnapshot() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("me@test.local").role(Role.USER).build();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountSettingsService.quotaSnapshot(userId))
                .thenReturn(new AccountQuotaSnapshot(128L, 512L, 384L, "FREE", "Free", null));

        AccountUsageResponse usage = accountService.usage();

        assertEquals(128L, usage.usedBytes());
        assertEquals(512L, usage.limitBytes());
        assertEquals(384L, usage.remainingBytes());
        assertEquals("FREE", usage.planCode());
        assertEquals("Free", usage.planName());
        assertNull(usage.quotaOverrideBytes());
    }

    @Test
    @DisplayName("usage throws unauthorized when the authenticated user no longer exists")
    void usage_missingCurrentUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> accountService.usage());
        verifyNoInteractions(accountSettingsService);
    }

    @Test
    @DisplayName("projects returns only projects owned by the current user")
    void projects_currentUser_returnsOwnedProjects() {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        ProjectOwnership owned = ProjectOwnership.builder()
                .projectId("owned")
                .ownerId(userId)
                .name("Owned Project")
                .sourceType(ProjectSourceType.LOCAL)
                .sizeBytes(1024L)
                .status(ProjectOwnershipStatus.ANALYZED)
                .createdAt(createdAt)
                .build();
        User user = User.builder().id(userId).email("me@test.local").role(Role.USER).build();
        AccountProjectPageRequest request = new AccountProjectPageRequest(0, 20);
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(projectOwnershipRepository.findByOwnerId(userId, request.toPageable()))
                .thenReturn(new PageImpl<>(java.util.List.of(owned), PageRequest.of(0, 20), 1));

        AccountProjectsPageResponse projects = accountService.projects(request);

        assertEquals(1, projects.items().size());
        assertEquals(0, projects.page());
        assertEquals(20, projects.size());
        assertEquals(1L, projects.totalElements());
        AccountProjectResponse project = projects.items().getFirst();
        assertEquals("owned", project.id());
        assertEquals("Owned Project", project.name());
        assertEquals("LOCAL", project.sourceType());
        assertEquals(1024L, project.sizeBytes());
        assertEquals("ANALYZED", project.status());
        assertEquals(createdAt, project.createdAt());
    }

    @Test
    @DisplayName("projects throws unauthorized when the authenticated user no longer exists")
    void projects_missingCurrentUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> accountService.projects(new AccountProjectPageRequest(0, 20)));
        verifyNoInteractions(projectOwnershipRepository);
    }
}
