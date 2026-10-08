package com.bloodbank.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Authentication and URL-level authorisation (requirements FR-24, NFR-06 to NFR-08).
 *
 * <p>There are <strong>two</strong> filter chains, because the two kinds of client
 * need different behaviour when they are not authenticated:
 *
 * <ul>
 *   <li><strong>{@code /api/**}</strong> — HTTP Basic, stateless, CSRF disabled.
 *       An unauthenticated call gets <strong>401</strong>, which is what a script
 *       can act on. This is what lets {@code scripts/health-check.sh}, the
 *       Selenium fixtures and the viva {@code curl} commands authenticate without
 *       first scraping a CSRF token out of an HTML page.</li>
 *   <li><strong>everything else</strong> — form login with a session and CSRF
 *       protection intact. An unauthenticated request is <strong>redirected</strong>
 *       to {@code /login}, which is what a browser needs.</li>
 * </ul>
 *
 * <p>Enabling Basic authentication on a single chain for both would make the whole
 * application answer 401 with a browser pop-up instead of redirecting to the login
 * page, which is why the split exists rather than being incidental.
 *
 * <p>Two further decisions:
 * <ol>
 *   <li><strong>{@code /actuator/health} is open.</strong> The
 *       container {@code HEALTHCHECK}, the Ansible {@code uri} assertion and the
 *       Jenkins post-deploy check all probe it with no credentials; requiring
 *       authentication there would make the container permanently unhealthy
 *       (FR-26).</li>
 *   <li><strong>URL rules are a coarse first filter, not the real guard.</strong>
 *       Which role may fulfil or reject a request is enforced in
 *       {@code RequestService}, because a URL pattern cannot express "ADMIN may
 *       move MATCHED to FULFILLED and nothing else" (NFR-07).</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(DemoUsersProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Accounts come from configuration and are hashed here, never stored in plain form. */
    @Bean
    InMemoryUserDetailsManager userDetailsService(DemoUsersProperties properties,
                                                  PasswordEncoder encoder) {
        List<UserDetails> users = properties.getUsers().stream()
                .map(account -> (UserDetails) User.withUsername(account.getUsername())
                        .password(encoder.encode(account.getPassword()))
                        .roles(account.getRole())
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    /**
     * Chain 1 — the machine-facing surface: the JSON API and the Actuator
     * endpoints. Basic, stateless, 401 when unauthenticated.
     *
     * <p>Actuator belongs here rather than on the browser chain. It is consumed
     * by scripts, by the container probe and by the Ansible health check, none
     * of which can follow a redirect to an HTML login form or scrape a CSRF
     * token. Leaving it on the browser chain meant an authenticated
     * {@code curl} against {@code /actuator/info} received the login page with
     * HTTP 200, which is both useless to a script and actively misleading.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**", "/actuator/**")
            .authorizeHttpRequests(auth -> auth
                // The probe stays open: the container HEALTHCHECK and the
                // Ansible check must reach it with no credentials (FR-26).
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Everything else Actuator exposes - build version, git revision,
                // environment - is operational detail, so it is ADMIN-only rather
                // than merely authenticated.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Everything else Actuator exposes - build version, git
                // revision, environment - is operational detail, so it is
                // ADMIN-only rather than merely authenticated.
                // /api/** and /actuator/** never reach this chain - they are
                // matched by the API chain above.
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Safe to disable only because this chain is stateless and
            // credential-per-request: there is no ambient session for a third-party
            // page to ride on, which is the attack CSRF protection exists to stop.
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /** Chain 2 — the browser application: form login, session, CSRF protection. */
    @Bean
    @Order(2)
    SecurityFilterChain webFilterChain(HttpSecurity http,
                                       RoleAwareSuccessHandler successHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // open: probes, the login page and static assets
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                // a donor sees only their own record
                .requestMatchers("/me", "/me/**").hasAnyRole("DONOR", "ADMIN")
                // registry, workflow and reporting screens
                .requestMatchers("/donors/**", "/requests/**", "/match", "/dashboard", "/")
                    .hasAnyRole("STAFF", "ADMIN")
                // /api/** and /actuator/** never reach this chain - they are
                // matched by the API chain above.
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                // Role-aware, not a single fixed URL: /dashboard is forbidden to
                // a DONOR, so a fixed success URL sent them straight to a 403
                // after a successful sign-in (BUG-07, found by Selenium J5).
                .successHandler(successHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?loggedOut")
                .permitAll())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
