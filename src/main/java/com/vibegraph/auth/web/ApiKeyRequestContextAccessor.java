package com.vibegraph.auth.web;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class ApiKeyRequestContextAccessor {

    public Optional<ApiKeyRequestContext> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(
                ApiKeyAuthFilter.API_KEY_CONTEXT_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return value instanceof ApiKeyRequestContext context ? Optional.of(context) : Optional.empty();
    }

    public void assertProjectMatches(String projectId) {
        current().ifPresent(context -> {
            if (context.projectId() == null || !context.projectId().equals(projectId)) {
                throw new com.vibegraph.common.exception.ForbiddenException("Access denied");
            }
        });
    }
}
