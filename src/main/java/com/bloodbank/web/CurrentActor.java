package com.bloodbank.web;

import com.bloodbank.service.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Translates Spring Security's {@link Authentication} into the domain's
 * {@link Actor}.
 *
 * <p>This exists so that the service layer never imports Spring Security. The
 * boundary keeps the workflow rules unit-testable by passing an actor directly,
 * and it keeps the question "who is allowed to do this?" expressed in the
 * domain's own vocabulary rather than in framework types.
 */
final class CurrentActor {

    private CurrentActor() {
    }

    static Actor from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("no authenticated user in the security context");
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .collect(Collectors.toSet());
        return new Actor(authentication.getName(), roles);
    }
}
