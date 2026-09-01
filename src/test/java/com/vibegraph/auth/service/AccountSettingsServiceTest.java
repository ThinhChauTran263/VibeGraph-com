package com.vibegraph.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vibegraph.auth.domain.entity.Plan;
import com.vibegraph.auth.domain.entity.User;
import com.vibegraph.auth.domain.entity.UserAccountSettings;
import com.vibegraph.auth.repository.PlanRepository;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.auth.repository.UserAccountSettingsRepository;
import com.vibegraph.common.exception.AccountBlockedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account settings service")
class AccountSettingsServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private UserAccountSettingsRepository settingsRepository;

    @Mock
    private ProjectUsageRepository projectUsageRepository;

    private AccountSettingsService service;
    private Plan freePlan;

    @BeforeEach
    void setUp() {
        service = new AccountSettingsService(planRepository, settingsRepository, projectUsageRepository);
        freePlan = Plan.builder()
                .id(UUID.randomUUID())
                .code("FREE")
                .name("Free")
                .storageLimitBytes(524_288_000L)
                .apiKeyLimit(3)
                .build();
    }

    @Test
    @DisplayName("createDefaultSettings creates FREE settings for new user")
    void createDefaultSettings_newUser_savesFreeSettings() {
        User user = User.builder().id(UUID.randomUUID()).email("new@test.local").build();
        when(settingsRepository.existsById(user.getId())).thenReturn(false);
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
        when(settingsRepository.save(any(UserAccountSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserAccountSettings settings = service.createDefaultSettings(user);

        assertEquals(user.getId(), settings.getUserId());
        assertEquals(freePlan, settings.getPlan());
        assertFalse(settings.isApiKeyCreationDisabled());
        assertNull(settings.getBlockedAt());
        verify(settingsRepository).save(any(UserAccountSettings.class));
    }

    @Test
    @DisplayName("createDefaultSettings returns existing settings without overwriting")
    void createDefaultSettings_existingUser_returnsExistingSettings() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("existing@test.local").build();
        UserAccountSettings existing = UserAccountSettings.builder().userId(userId).plan(freePlan).build();
        when(settingsRepository.existsById(userId)).thenReturn(true);
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(existing));

        UserAccountSettings settings = service.createDefaultSettings(user);

        assertSame(existing, settings);
        verify(settingsRepository, never()).save(any());
        verifyNoInteractions(planRepository);
    }

    @Test
    @DisplayName("isBlocked returns true only when blocked settings exist")
    void isBlocked_returnsBlockedState() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder().userId(userId).plan(freePlan).build();
        settings.block("policy violation");
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        when(settingsRepository.findById(new UUID(0L, 0L))).thenReturn(Optional.empty());

        assertTrue(service.isBlocked(userId));
        assertFalse(service.isBlocked(new UUID(0L, 0L)));
    }

    @Test
    @DisplayName("assertNotBlocked throws ACCOUNT_BLOCKED when blocked")
    void assertNotBlocked_blockedUser_throws() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId)
                .plan(freePlan)
                .blockedReason("admin-only internal note")
                .blockedReasonSafe("policy violation")
                .build();
        settings.block("admin-only internal note", "policy violation");
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        AccountBlockedException ex = assertThrows(
                AccountBlockedException.class,
                () -> service.assertNotBlocked(userId));

        assertEquals("ACCOUNT_BLOCKED", ex.getCode());
        assertEquals("policy violation", ex.getSafeReason());
    }

    @Test
    @DisplayName("assertNotBlocked never exposes internal blocked reason without a safe reason")
    void assertNotBlocked_withoutSafeReason_usesDefaultReason() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder()
                .userId(userId)
                .plan(freePlan)
                .blockedReason("internal fraud review note")
                .build();
        settings.block("internal fraud review note");
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        AccountBlockedException ex = assertThrows(
                AccountBlockedException.class,
                () -> service.assertNotBlocked(userId));

        assertEquals("Account is blocked", ex.getSafeReason());
    }

    @Test
    @DisplayName("quotaSnapshot returns plan and remaining source storage")
    void quotaSnapshot_returnsEffectiveQuota() {
        UUID userId = UUID.randomUUID();
        UserAccountSettings settings = UserAccountSettings.builder().userId(userId).plan(freePlan).build();
        when(settingsRepository.findById(userId)).thenReturn(Optional.of(settings));
        when(projectUsageRepository.sumStorageBytesByOwnerId(userId)).thenReturn(128L);

        AccountQuotaSnapshot snapshot = service.quotaSnapshot(userId);

        assertEquals(128L, snapshot.usedBytes());
        assertEquals(524_288_000L, snapshot.limitBytes());
        assertEquals(524_287_872L, snapshot.remainingBytes());
        assertEquals("FREE", snapshot.planCode());
        assertEquals("Free", snapshot.planName());
        assertNull(snapshot.quotaOverrideBytes());
    }
}
