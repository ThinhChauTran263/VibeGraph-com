package com.vibegraph.mcp.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vibegraph.auth.web.ApiKeyRequestContext;
import com.vibegraph.auth.web.ApiKeyRequestContextAccessor;
import com.vibegraph.common.ownership.ProjectOwnershipQuery;
import com.vibegraph.graph.repository.GraphRepository;
import com.vibegraph.graph.repository.ProjectMetadata;

class ProjectDirectoryServiceApiKeyScopeTest {

    @Test
    void listProjects_ProjectBoundApiKey_ReturnsOnlyBoundProject() {
        ProjectOwnershipQuery ownershipQuery = org.mockito.Mockito.mock(ProjectOwnershipQuery.class);
        GraphRepository graphRepository = org.mockito.Mockito.mock(GraphRepository.class);
        ApiKeyRequestContextAccessor contextAccessor = org.mockito.Mockito.mock(ApiKeyRequestContextAccessor.class);
        when(ownershipQuery.ownedProjectIds()).thenReturn(List.of("p1", "p2"));
        when(contextAccessor.current()).thenReturn(java.util.Optional.of(new ApiKeyRequestContext("key-1", "p1")));
        when(graphRepository.findAllProjects()).thenReturn(List.of(
                new ProjectMetadata("p1", "One", null, null, null, 1, 2, 3),
                new ProjectMetadata("p2", "Two", null, null, null, 1, 2, 3)));

        ProjectDirectoryServiceImpl service =
                new ProjectDirectoryServiceImpl(ownershipQuery, graphRepository, contextAccessor);

        assertThat(service.listProjects().getProjects())
                .extracting(project -> project.getId())
                .containsExactly("p1");
    }
}
