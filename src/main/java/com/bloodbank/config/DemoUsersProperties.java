package com.bloodbank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * The portal's user accounts, bound from configuration.
 *
 * <p>Accounts are defined in {@code application.yml} and can be overridden by
 * environment variables, so no password appears in Java source (requirement
 * NFR-06). The values configured are passwords for a locally deployed
 * demonstration instance; they are hashed with BCrypt at startup and never
 * stored or logged in plain form.
 *
 * <p>A user-management screen is out of MVP scope: three fixed roles are what the
 * role-based workflow needs, and nothing in the assessed behaviour depends on
 * being able to create users at run time.
 */
@ConfigurationProperties(prefix = "bloodbank.security")
public class DemoUsersProperties {

    /** Configured accounts. */
    private List<Account> users = new ArrayList<>();

    public List<Account> getUsers() { return users; }
    public void setUsers(List<Account> users) { this.users = users; }

    public static class Account {
        private String username;
        private String password;
        /** One of ADMIN, STAFF, DONOR. */
        private String role;
        /** For a DONOR account, the donor record this login represents. */
        private Long donorId;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public Long getDonorId() { return donorId; }
        public void setDonorId(Long donorId) { this.donorId = donorId; }
    }
}
