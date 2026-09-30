package com.bloodbank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Skeleton security configuration (Task 4).
 *
 * <p>At this stage the application has no domain screens yet, so everything is
 * open and only the shape of the configuration is established. Form login,
 * BCrypt password encoding and the three roles (ADMIN / STAFF / DONOR) that the
 * role-based request workflow depends on arrive with the workflow itself in
 * Task 6 (requirements FR-24 and NFR-06/NFR-07).
 *
 * <p>{@code /actuator/health} is left unauthenticated from the start because the
 * container HEALTHCHECK and the Ansible health check must be able to reach it
 * without credentials (FR-26).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().permitAll())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
