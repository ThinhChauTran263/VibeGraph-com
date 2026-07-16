package com.vibegraph.auth.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record AccountProjectsPageResponse(
        List<AccountProjectResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static AccountProjectsPageResponse from(Page<AccountProjectResponse> projects) {
        return new AccountProjectsPageResponse(
                projects.getContent(),
                projects.getNumber(),
                projects.getSize(),
                projects.getTotalElements(),
                projects.getTotalPages());
    }
}
