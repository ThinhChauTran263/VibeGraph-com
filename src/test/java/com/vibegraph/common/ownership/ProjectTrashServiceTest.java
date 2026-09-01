package com.vibegraph.common.ownership;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.entity.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ProjectNotFoundException;
import com.vibegraph.graph.config.ProjectsProperties;
import com.vibegraph.graph.dto.TrashedProjectResponse;

/**
 * Unit tests for {@link ProjectTrashService}: owner-scoped trash operations, the re-import replace
 * rule, and a retention sweep that survives individual failures.
 */
@DisplayName("ProjectTrashService")
class ProjectTrashServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    private ProjectOwnershipRepository ownershipRepository;
    private ProjectDeletionOrchestrator deletionOrchestrator;
    private ProjectsProperties projectsProperties;
    private CurrentUser currentUser;
    private ProjectTrashService service;

    @BeforeEach
    void setUp() {
        ownershipRepository = Mockito.mock(ProjectOwnershipRepository.class);
        deletionOrchestrator = Mockito.mock(ProjectDeletionOrchestrator.class);
        projectsProperties = new ProjectsProperties();
        currentUser = Mockito.mock(CurrentUser.class);
        Mockito.lenient().when(currentUser.id()).thenReturn(OWNER);
        service = new ProjectTrashService(ownershipRepository, deletionOrchestrator, projectsProperties,
                currentUser, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ProjectOwnership trashed(String projectId, String name, Instant deletedAt) {
        ProjectOwnership ownership = new ProjectOwnership();
        ownership.setProjectId(projectId);
        ownership.setOwnerId(OWNER);
        ownership.setName(name);
        ownership.setSourceType(ProjectSourceType.GITHUB);
        ownership.setSizeBytes(4096L);
        ownership.setDeletedAt(deletedAt);
        return ownership;
    }

    @Test
    @DisplayName("listTrash reports when each project will be purged and how many days are left")
    void listTrashReportsTheCountdown() {
        // Trashed 25 hours ago with a 3-day retention → purged in ~47 hours, i.e. 1 whole day left.
        when(ownershipRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(OWNER))
                .thenReturn(List.of(trashed("p1", "acme/widgets", NOW.minusSeconds(25 * 3600))));

        List<TrashedProjectResponse> trash = service.listTrash();

        assertThat(trash).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo("p1");
            assertThat(entry.name()).isEqualTo("acme/widgets");
            assertThat(entry.sourceType()).isEqualTo("GITHUB");
            assertThat(entry.purgeAt()).isEqualTo(NOW.minusSeconds(25 * 3600).plusSeconds(3 * 86400));
            assertThat(entry.daysRemaining()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("listTrash never shows a negative countdown for an overdue project")
    void listTrashClampsOverdueCountdownToZero() {
        when(ownershipRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(OWNER))
                .thenReturn(List.of(trashed("p1", "acme/widgets", NOW.minusSeconds(10 * 86400))));

        assertThat(service.listTrash()).singleElement()
                .extracting(TrashedProjectResponse::daysRemaining)
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("restore requires the project to be in the caller's own trash")
    void restoreRejectsProjectsOutsideTheCallersTrash() {
        when(ownershipRepository.findByProjectIdAndOwnerIdAndDeletedAtIsNotNull("p1", OWNER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore("p1"))
                .isInstanceOf(ProjectNotFoundException.class);

        verify(deletionOrchestrator, never()).restore("p1");
    }

    @Test
    @DisplayName("restore delegates to the orchestrator once ownership is proven")
    void restoreDelegatesForOwnedTrashedProject() {
        when(ownershipRepository.findByProjectIdAndOwnerIdAndDeletedAtIsNotNull("p1", OWNER))
                .thenReturn(Optional.of(trashed("p1", "acme/widgets", NOW.minusSeconds(60))));

        service.restore("p1");

        verify(deletionOrchestrator).restore("p1");
    }

    @Test
    @DisplayName("purge requires the project to be in the caller's own trash")
    void purgeRejectsProjectsOutsideTheCallersTrash() {
        when(ownershipRepository.findByProjectIdAndOwnerIdAndDeletedAtIsNotNull("p1", OWNER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purge("p1"))
                .isInstanceOf(ProjectNotFoundException.class);

        verify(deletionOrchestrator, never()).purge("p1");
    }

    @Test
    @DisplayName("re-importing a repository purges the trashed copy of the same repository")
    void purgeTrashedGitHubDuplicatesRemovesTheOldCopy() {
        when(ownershipRepository.findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNotNull(
                OWNER, ProjectSourceType.GITHUB, "acme/widgets"))
                .thenReturn(List.of(trashed("old", "acme/widgets", NOW.minusSeconds(3600))));

        assertThat(service.purgeTrashedGitHubDuplicates(OWNER, "acme/widgets")).containsExactly("old");
        verify(deletionOrchestrator).purge("old");
    }

    @Test
    @DisplayName("a blank repository name matches nothing and touches no project")
    void purgeTrashedGitHubDuplicatesIgnoresBlankNames() {
        assertThat(service.purgeTrashedGitHubDuplicates(OWNER, "  ")).isEmpty();

        Mockito.verifyNoInteractions(ownershipRepository, deletionOrchestrator);
    }

    @Test
    @DisplayName("a duplicate that cannot be purged is reported as not replaced")
    void purgeTrashedGitHubDuplicatesReportsOnlyWhatItRemoved() {
        when(ownershipRepository.findByOwnerIdAndSourceTypeAndNameAndDeletedAtIsNotNull(
                OWNER, ProjectSourceType.GITHUB, "acme/widgets"))
                .thenReturn(List.of(trashed("locked", "acme/widgets", NOW.minusSeconds(3600))));
        doThrow(new RuntimeException("admin-locked key")).when(deletionOrchestrator).purge("locked");

        assertThat(service.purgeTrashedGitHubDuplicates(OWNER, "acme/widgets")).isEmpty();
    }

    @Test
    @DisplayName("the sweep purges every project whose retention window has expired, batch by batch")
    void sweepPurgesExpiredProjects() {
        when(ownershipRepository.findByDeletedAtLessThan(eq(NOW.minusSeconds(3 * 86400)), any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(List.of(
                                trashed("p1", "acme/widgets", NOW.minusSeconds(4 * 86400)),
                                trashed("p2", "acme/gadgets", NOW.minusSeconds(5 * 86400)))),
                        Page.empty());

        service.purgeExpiredProjects();

        verify(deletionOrchestrator).purge("p1");
        verify(deletionOrchestrator).purge("p2");
    }

    @Test
    @DisplayName("one failing project does not stop the rest of the sweep, and the sweep terminates")
    void sweepContinuesAfterAFailure() {
        // Batch 1: locked fails, p2 succeeds (batch made progress → sweep continues).
        // Batch 2: only locked remains, zero progress → sweep stops instead of looping forever.
        when(ownershipRepository.findByDeletedAtLessThan(eq(NOW.minusSeconds(3 * 86400)), any(Pageable.class)))
                .thenReturn(
                        new PageImpl<>(List.of(
                                trashed("locked", "acme/widgets", NOW.minusSeconds(4 * 86400)),
                                trashed("p2", "acme/gadgets", NOW.minusSeconds(5 * 86400)))),
                        new PageImpl<>(List.of(
                                trashed("locked", "acme/widgets", NOW.minusSeconds(4 * 86400)))));
        doThrow(new RuntimeException("admin-locked key")).when(deletionOrchestrator).purge("locked");

        service.purgeExpiredProjects();

        // The failing project stays in trash and is retried next run; the rest is still purged.
        verify(deletionOrchestrator).purge("p2");
        verify(deletionOrchestrator, times(2)).purge("locked");
    }

    @Test
    @DisplayName("the sweep honours a configured retention window")
    void sweepUsesTheConfiguredRetentionWindow() {
        projectsProperties.setTrashRetentionDays(7);
        when(ownershipRepository.findByDeletedAtLessThan(eq(NOW.minusSeconds(7 * 86400)), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.purgeExpiredProjects();

        verify(ownershipRepository).findByDeletedAtLessThan(eq(NOW.minusSeconds(7 * 86400)), any(Pageable.class));
        Mockito.verifyNoInteractions(deletionOrchestrator);
    }
}
