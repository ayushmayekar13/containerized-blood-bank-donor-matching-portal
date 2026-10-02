package com.bloodbank.web.api;

import com.bloodbank.service.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a domain {@link Actor} from the API's {@link Authentication}.
 *
 * <p>The same translation the HTML controllers use, kept here so that the service
 * layer never depends on Spring Security in either entry path.
 */
final class ActorAdapter {

    private ActorAdapter() {
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
