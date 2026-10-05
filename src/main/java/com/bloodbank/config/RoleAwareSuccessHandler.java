package com.bloodbank.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends each role to a landing page it is actually allowed to open.
 *
 * <p>Defect <strong>BUG-07</strong>, found by Selenium journey J5: login used a
 * single fixed success URL of {@code /dashboard}, which `STAFF` and `ADMIN` may
 * open but `DONOR` may not. A donor signing in with correct credentials was
 * therefore shown an HTTP 403 page — authentication succeeded and the
 * application immediately told them they were not allowed in.
 *
 * <p>Every unit test passed while this was broken, because each one authenticated
 * directly as a role and requested a specific URL; none of them walked the login
 * form and followed the redirect. It took a browser to find it, which is the
 * clearest argument in this project for having a UI suite at all.
 *
 * <p>Extending {@link SavedRequestAwareAuthenticationSuccessHandler} rather than
 * writing a handler from scratch keeps the useful default behaviour: a user who
 * was deep-linked to a page, bounced to login, and signed in successfully is
 * returned to the page they originally asked for. Only the fallback — where there
 * is no saved request — becomes role-aware.
 */
@Component
public class RoleAwareSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public RoleAwareSuccessHandler() {
        // Used when the user came straight to /login rather than being bounced
        // there from a protected page.
        setDefaultTargetUrl("/dashboard");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication)
            throws ServletException, IOException {

        boolean donorOnly = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(role -> role.equals("ROLE_STAFF") || role.equals("ROLE_ADMIN"));

        if (donorOnly) {
            // A donor's only screen is their own profile. Overriding the target
            // and forcing it ignores any saved request as well, which is correct:
            // whatever page they were refused before signing in, they are still
            // not allowed to see it.
            setDefaultTargetUrl("/me");
            setAlwaysUseDefaultTargetUrl(true);
        } else {
            setDefaultTargetUrl("/dashboard");
            setAlwaysUseDefaultTargetUrl(false);
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
