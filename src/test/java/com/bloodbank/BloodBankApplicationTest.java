package com.bloodbank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test for the skeleton (Task 4).
 *
 * <p>Deliberately minimal: it proves the Spring context starts, the datasource
 * is reachable and the two endpoints every later task depends on are served.
 * If this test fails, nothing further in the pipeline is worth debugging.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BloodBankApplicationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Spring context loads with the JPA datasource configured")
    void contextLoads() {
        // Context startup is the assertion: @SpringBootTest fails the test if the
        // application context cannot be built.
    }

    @Test
    @DisplayName("FR-24: an unauthenticated request for a portal page is redirected to login")
    void portalPagesRequireAuthentication() throws Exception {
        // The skeleton's open landing page was replaced by the dashboard in
        // Task 6, so "/" is now a protected screen.
        mvc.perform(get("/"))
           .andExpect(status().is3xxRedirection())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                   .redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("The login page is served without authentication and identifies the project")
    void loginPageIsOpen() throws Exception {
        mvc.perform(get("/login"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("Blood Bank Donor Matching Portal")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("23102A0018")));
    }

    @Test
    @DisplayName("Health endpoint is reachable without credentials and reports UP")
    void healthEndpointIsOpen() throws Exception {
        // FR-26: the container HEALTHCHECK and the Ansible health check must be
        // able to read this without authentication.
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }
}
