package com.vibegraph.common.ownership;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibegraph.auth.CurrentUser;
import com.vibegraph.auth.domain.ProjectOwnership;
import com.vibegraph.auth.domain.ProjectSourceType;
import com.vibegraph.auth.repository.ProjectOwnershipRepository;
import com.vibegraph.common.exception.ForbiddenException;

/**
 * Unit tests for {@link ProjectOwnershipRegistrar}, focused on the invariant that ownership is
 * never transferred between users.
 */
@DisplayName("ProjectOwnershipRegistrar")
class ProjectOwnershipRegistrarTest {

    private ProjectOwnershipRepository ownershipRepository;
    private CurrentUser currentUser;
    private ProjectOwnershipRegistrar registrar;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ownershipRepository = Mockito.mock(ProjectOwnershipRepository.class);
        currentUser = Mockito.mock(CurrentUser.class);
        registrar = new ProjectOwnershipRegistrar(ownershipRepository, currentUser);
    }

    @Test
    @DisplayName("no existing row -> creates ownership for the current user")
    void createsWhenAbsent() {
        when(currentUser.id()).thenReturn(userA);
        when(ownershipRepository.findById("p1")).thenReturn(Optional.empty());

        registrar.registerLocal("p1", "Project 1");

        ArgumentCaptor<ProjectOwnership> captor = ArgumentCaptor.forClass(ProjectOwnership.class);
        verify(ownershipRepository).save(captor.capture());
        ProjectOwnership saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo("p1");
        assertThat(saved.getOwnerId()).isEqualTo(userA);
        assertThat(saved.getName()).isEqualTo("Project 1");
        assertThat(saved.getSourceType()).isEqualTo(ProjectSourceType.LOCAL);
    }

    @Test
    @DisplayName("existing row, same owner -> idempotent metadata refresh, owner unchanged")
    void idempotentForSameOwner() {
        when(currentUser.id()).thenReturn(userA);
        ProjectOwnership existing = ProjectOwnership.builder()
                .projectId("p1").ownerId(userA).name("old").sourceType(ProjectSourceType.LOCAL)
                .build();
        when(ownershipRepository.findById("p1")).thenReturn(Optional.of(existing));

        registrar.registerArchive("p1", "new name");

        ArgumentCaptor<ProjectOwnership> captor = ArgumentCaptor.forClass(ProjectOwnership.class);
        verify(ownershipRepository).save(captor.capture());
        ProjectOwnership saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(userA);
        assertThat(saved.getName()).isEqualTo("new name");
        assertThat(saved.getSourceType()).isEqualTo(ProjectSourceType.ARCHIVE);
    }

    @Test
    @DisplayName("existing row, different owner -> ForbiddenException and no owner reassignment")
    void refusesOwnerTransfer() {
        when(currentUser.id()).thenReturn(userB);
        ProjectOwnership existing = ProjectOwnership.builder()
                .projectId("p1").ownerId(userA).name("A's project").sourceType(ProjectSourceType.LOCAL)
                .build();
        when(ownershipRepository.findById("p1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> registrar.registerGithub("p1", "hijack"))
                .isInstanceOf(ForbiddenException.class);

        verify(ownershipRepository, never()).save(any());
        assertThat(existing.getOwnerId()).isEqualTo(userA);
    }

    @Test
    @DisplayName("GitHub registration stores the imported branch and commit SHA")
    void storesBranchAndSha() {
        when(currentUser.id()).thenReturn(userA);
        when(ownershipRepository.findById("p1")).thenReturn(Optional.empty());

        registrar.registerGithub("p1", "acme/demo", "sha-1", "develop");

        ArgumentCaptor<ProjectOwnership> captor = ArgumentCaptor.forClass(ProjectOwnership.class);
        verify(ownershipRepository).save(captor.capture());
        ProjectOwnership saved = captor.getValue();
        assertThat(saved.getSourceType()).isEqualTo(ProjectSourceType.GITHUB);
        assertThat(saved.getSourceRef()).isEqualTo("sha-1");
        assertThat(saved.getSourceBranch()).isEqualTo("develop");
    }
}
