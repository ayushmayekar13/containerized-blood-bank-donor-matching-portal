package com.bloodbank.web;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.Donor;
import com.bloodbank.domain.Urgency;
import com.bloodbank.repo.BloodRequestRepository;
import com.bloodbank.repo.DonorRepository;
import com.bloodbank.service.Actor;
import com.bloodbank.service.RequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requirements FR-24 to FR-26 and NFR-07: authentication, the two filter chains,
 * and the HTTP status-code contract in {@code docs/API.md} section 5.
 *
 * <p>These tests assert over HTTP rather than against the services, because the
 * claim being made is specifically that the <strong>API cannot bypass</strong> the
 * restrictions the screens appear to apply. Proving that requires going through
 * the same filter chain a {@code curl} command would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("FR-24..26 authentication, filter chains and status-code contract")
class SecurityRoleTest {

    @Autowired private MockMvc mvc;
    @Autowired private RequestService requests;
    @Autowired private DonorRepository donorRepo;
    @Autowired private BloodRequestRepository requestRepo;

    private Long newRequestId;
    private Long matchedRequestId;
    private Long donorId;

    @BeforeEach
    void seed() {
        requestRepo.deleteAll();
        donorRepo.deleteAll();

        Donor donor = new Donor("Sneha Patil", BloodGroup.B_POS, "9820040001",
                                "sneha@example.com", "Thane");
        donor.setLastDonationDate(LocalDate.now().minusDays(150));
        donorId = donorRepo.save(donor).getId();

        Actor staff = Actor.of("staff", Actor.ROLE_STAFF);
        newRequestId = requests.create(new BloodRequest("Meera Joshi", BloodGroup.B_POS, 2,
                "Civil Hospital", "Thane", Urgency.HIGH), staff).getId();

        BloodRequest toMatch = requests.create(new BloodRequest("Aditya Kamat", BloodGroup.B_POS, 1,
                "Sion Hospital", "Mumbai", Urgency.CRITICAL), staff);
        matchedRequestId = requests.assignDonor(toMatch.getId(), donorId, staff).getId();
    }

    // ------------------------------------------------- chain 2: browser requests

    @Nested
    @DisplayName("the browser chain redirects to the login page")
    class BrowserChain {

        @ParameterizedTest(name = "GET {0} unauthenticated is redirected to login")
        @ValueSource(strings = {"/", "/dashboard", "/donors", "/requests", "/match", "/me"})
        @DisplayName("FR-24: protected screens require authentication")
        void protectedScreensRedirect(String path) throws Exception {
            mvc.perform(get(path))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
        }

        @ParameterizedTest(name = "GET {0} is open")
        @ValueSource(strings = {"/login", "/actuator/health", "/css/app.css"})
        @DisplayName("the login page, the health probe and static assets are open")
        void openPaths(String path) throws Exception {
            mvc.perform(get(path)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("FR-26: the health probe answers without credentials, so the container can stay healthy")
        void healthIsOpenAndReportsUp() throws Exception {
            mvc.perform(get("/actuator/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("a DONOR may not open the donor registry")
        void donorCannotSeeTheRegistry() throws Exception {
            // A signed-in session is used here rather than Basic credentials,
            // because Basic authentication is deliberately configured on the API
            // chain only: sending it to a browser screen is simply ignored and the
            // request is redirected to the login page, which is asserted below.
            mvc.perform(get("/donors").with(user("donor").roles("DONOR")))
               .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Basic credentials do not authenticate a browser screen - they are scoped to /api/**")
        void basicAuthDoesNotApplyToBrowserScreens() throws Exception {
            mvc.perform(get("/donors").with(httpBasic("staff", "test-staff")))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("a signed-in STAFF user may open the donor registry")
        void staffCanSeeTheRegistry() throws Exception {
            mvc.perform(get("/donors").with(user("staff").roles("STAFF")))
               .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a DONOR is permitted at /me, which is the one screen their role owns")
        void donorIsPermittedAtOwnProfile() throws Exception {
            // Asserting "not 403" rather than "200": whether the page renders
            // depends on the configured account being linked to an existing donor
            // record, which is seed data, not an authorisation rule. The rule
            // under test here is that the DONOR role is allowed through.
            mvc.perform(get("/me").with(user("donor").roles("DONOR")))
               .andExpect(result -> org.assertj.core.api.Assertions
                       .assertThat(result.getResponse().getStatus())
                       .as("DONOR must not be forbidden from /me")
                       .isNotEqualTo(403));
        }

        @Test
        @DisplayName("a STAFF user may not read the Actuator endpoints beyond health")
        void staffCannotReadActuator() throws Exception {
            mvc.perform(get("/actuator/info").with(user("staff").roles("STAFF")))
               .andExpect(status().isForbidden());
        }
    }

    // ----------------------------------------------------- chain 1: the JSON API

    @Nested
    @DisplayName("the API chain answers 401, not a redirect")
    class ApiChain {

        @Test
        @DisplayName("an unauthenticated API call is 401, which a script can act on")
        void unauthenticatedApiCallIs401() throws Exception {
            mvc.perform(get("/api/donors")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("HTTP Basic authenticates the API without needing a CSRF token")
        void basicAuthWorksOnTheApi() throws Exception {
            mvc.perform(get("/api/donors").with(httpBasic("staff", "test-staff")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].fullName").value("Sneha Patil"))
               .andExpect(jsonPath("$[0].eligible").value(true));
        }

        @Test
        @DisplayName("a POST transition needs no CSRF token on the API chain")
        void postNeedsNoCsrfToken() throws Exception {
            mvc.perform(post("/api/requests/{id}/match", newRequestId)
                            .with(httpBasic("staff", "test-staff"))
                            .contentType("application/json")
                            .content("{\"donorId\": " + donorId + "}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("MATCHED"));
        }
    }

    // -------------------------------------------------- the status-code contract

    @Nested
    @DisplayName("NFR-07: the API enforces the same rules as the screens")
    class StatusCodeContract {

        @Test
        @DisplayName("403: STAFF calling fulfil is refused - this is the viva's proof that the guard is server-side")
        void staffFulfilIs403() throws Exception {
            mvc.perform(post("/api/requests/{id}/fulfil", matchedRequestId)
                            .with(httpBasic("staff", "test-staff")))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.error").value("InsufficientRole"))
               .andExpect(jsonPath("$.message").value(
                       org.hamcrest.Matchers.containsString("requires ADMIN")));
        }

        @Test
        @DisplayName("409: ADMIN fulfilling a NEW request is an illegal transition")
        void adminFulfilOfNewRequestIs409() throws Exception {
            mvc.perform(post("/api/requests/{id}/fulfil", newRequestId)
                            .with(httpBasic("admin", "test-admin")))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.error").value("IllegalTransition"))
               .andExpect(jsonPath("$.message").value(
                       org.hamcrest.Matchers.containsString("cannot move from NEW to FULFILLED")));
        }

        @Test
        @DisplayName("200: ADMIN fulfilling a MATCHED request succeeds")
        void adminFulfilOfMatchedRequestSucceeds() throws Exception {
            mvc.perform(post("/api/requests/{id}/fulfil", matchedRequestId)
                            .with(httpBasic("admin", "test-admin")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("FULFILLED"))
               .andExpect(jsonPath("$.allowedNext").isEmpty());
        }

        @Test
        @DisplayName("404: an unknown request id")
        void unknownRequestIs404() throws Exception {
            mvc.perform(get("/api/requests/999999").with(httpBasic("staff", "test-staff")))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.error").value("NotFound"));
        }

        @Test
        @DisplayName("400: a validation failure lists the offending fields")
        void validationFailureIs400WithFieldErrors() throws Exception {
            mvc.perform(post("/api/donors").with(httpBasic("staff", "test-staff"))
                            .contentType("application/json")
                            .content("""
                                     {"fullName":"X","bloodGroup":"B_POS","phone":"12345",
                                      "email":"not-an-email","city":"T"}
                                     """))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("ValidationFailed"))
               .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }

        @Test
        @DisplayName("409: a duplicate phone number")
        void duplicatePhoneIs409() throws Exception {
            mvc.perform(post("/api/donors").with(httpBasic("staff", "test-staff"))
                            .contentType("application/json")
                            .content("""
                                     {"fullName":"Impostor Donor","bloodGroup":"O_POS",
                                      "phone":"9820040001","email":"impostor@example.com",
                                      "city":"Thane","available":true}
                                     """))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.error").value("DuplicatePhone"));
        }

        @Test
        @DisplayName("422: assigning a donor who is incompatible, unavailable or inside the 90-day interval")
        void invalidMatchIs422() throws Exception {
            Donor ineligible = new Donor("Zoya Qureshi", BloodGroup.B_POS, "9820040002",
                                         "zoya@example.com", "Thane");
            ineligible.setLastDonationDate(LocalDate.now().minusDays(10));
            Long ineligibleId = donorRepo.save(ineligible).getId();

            mvc.perform(post("/api/requests/{id}/match", newRequestId)
                            .with(httpBasic("staff", "test-staff"))
                            .contentType("application/json")
                            .content("{\"donorId\": " + ineligibleId + "}"))
               .andExpect(status().isUnprocessableEntity())
               .andExpect(jsonPath("$.error").value("InvalidMatch"));
        }

        @Test
        @DisplayName("every error body carries the documented shape")
        void errorBodyShape() throws Exception {
            mvc.perform(get("/api/requests/999999").with(httpBasic("staff", "test-staff")))
               .andExpect(jsonPath("$.timestamp").exists())
               .andExpect(jsonPath("$.status").value(404))
               .andExpect(jsonPath("$.error").exists())
               .andExpect(jsonPath("$.message").exists())
               .andExpect(jsonPath("$.path").value("/api/requests/999999"))
               .andExpect(jsonPath("$.fieldErrors").isArray());
        }
    }
}
