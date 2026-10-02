package com.bloodbank.service;

import java.util.Set;

/**
 * The user performing an operation, reduced to the two things the business rules
 * need: a username for the audit trail and a set of roles for authorisation.
 *
 * <p>Services take an {@code Actor} rather than reading Spring Security's
 * {@code SecurityContextHolder} themselves. That has two consequences worth
 * stating explicitly:
 * <ul>
 *   <li>The workflow rules can be unit-tested by passing an actor, with no
 *       security context, no mock authentication and no web request.</li>
 *   <li>Authorisation is enforced <em>in the service layer</em>, so the REST API
 *       cannot bypass a restriction that the HTML screens appear to apply
 *       (requirement NFR-07). Hiding a button is presentation, not security.</li>
 * </ul>
 */
public record Actor(String username, Set<String> roles) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_DONOR = "DONOR";

    public Actor {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("an acting username is required for the audit trail");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static Actor of(String username, String... roles) {
        return new Actor(username, Set.of(roles));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(ROLE_ADMIN);
    }

    /** Admins can do everything staff can do, so staff capability is the union. */
    public boolean isStaffOrAdmin() {
        return hasRole(ROLE_STAFF) || hasRole(ROLE_ADMIN);
    }
}
