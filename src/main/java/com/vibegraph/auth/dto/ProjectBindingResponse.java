package com.vibegraph.auth.dto;

import com.vibegraph.auth.domain.ProjectOwnership;

/** Safe project binding projection for API-key clients. */
public record ProjectBindingResponse(String id, String name, String sourceType, String status) {

    public static ProjectBindingResponse from(ProjectOwnership project) {
        return new ProjectBindingResponse(
                project.getProjectId(),
                project.getName(),
                project.getSourceType() == null ? null : project.getSourceType().name(),
                project.getStatus() == null ? null : project.getStatus().name());
    }
}
