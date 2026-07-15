package com.vibegraph.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.vibegraph.auth.service.AuthenticatedUser;
import com.vibegraph.common.exception.UnauthorizedException;

/**
 * Resolves the current authenticated user id from the {@code SecurityContext}.
 *
 * <p>The principal is the {@link AuthenticatedUser} placed there by the JWT filter. Controllers
 * and services derive identity from here — never from a request-supplied id — which is what keeps
 * ownership enforcement trustworthy in later slices.
 */
@Component
public class CurrentUser {

    /**
     * @return the authenticated user's id
     * @throws UnauthorizedException if there is no authenticated principal
     */
    public UUID id() {
        return principal().id();
    }

    /**
     * @return the full authenticated principal (id, email, role)
     * @throws UnauthorizedException if there is no authenticated principal
     */
    public AuthenticatedUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new UnauthorizedException("No authenticated user");
    }
}
