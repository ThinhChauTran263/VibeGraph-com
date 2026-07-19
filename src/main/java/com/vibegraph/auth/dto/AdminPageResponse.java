package com.vibegraph.auth.dto;

import java.util.List;

public record AdminPageResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int pageNumber,
    int pageSize
) {}
