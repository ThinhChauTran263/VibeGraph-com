package com.vibegraph.auth.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record AccountProjectPageRequest(int page, int size) {

    public AccountProjectPageRequest {
        if (size == 0) {
            size = 20;
        }
    }

    public Pageable toPageable() {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("projectId"));
        return PageRequest.of(page, size, sort);
    }
}
