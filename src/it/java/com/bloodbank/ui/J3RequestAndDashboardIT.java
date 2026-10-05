package com.bloodbank.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 3 — recording a blood request, and seeing it reflected on the dashboard.
 *
 * <p><strong>Why this journey is critical:</strong> it is the only journey that
 * crosses a screen boundary. A request created on one page has to appear in the
 * aggregate on another, which is exactly where defect BUG-02 lived — the
 * dashboard had its own supply calculation and disagreed with the shortlist the
 * technician was reading.
 *
 * <p>Covers AC-05.1 (a new request is created in `NEW` with a timestamp),
 * AC-05.3 (it appears on the dashboard) and AC-08.1 (the dashboard counts
 * callable donors per group).
 */
@DisplayName("J3 - Record a blood request and see it on the dashboard")
class J3RequestAndDashboardIT extends BaseJourney {

    @Test
    @DisplayName("AC-05.1 / AC-05.3 a recorded request starts in NEW and appears among the open requests")
    void recordsARequestAndSeesItOnTheDashboard() {
        String suffix = unique();
        String patient = "Journey Patient " + suffix;

        signInAsStaff();

        open("/dashboard");
        int openBefore = Integer.parseInt(textOf("tile-open-requests"));

        open("/requests/new");
        type("field-patientName", patient);
        select("field-request-bloodGroup", "B+");
        type("field-unitsNeeded", "2");
        type("field-hospital", "Civil Hospital, Thane");
        type("field-request-city", "Thane");
        select("field-urgency", "High");
        click("save-request");

        // The application lands on the request detail page, which is where the
        // state and the audit trail are visible.
        waitForText("request-detail-patient", patient);
        assertThat(textOf("request-detail-status"))
                .as("FR-11: a newly recorded request must start in New")
                .isEqualTo("New");
        assertThat(textOf("matched-donor"))
                .as("a new request has nobody assigned yet")
                .containsIgnoringCase("not yet matched");
        assertThat(textOf("created-by"))
                .as("NFR-15: the request must record who recorded it")
                .isEqualTo("staff");

        // Crossing the screen boundary: the aggregate must have moved.
        open("/dashboard");
        assertThat(Integer.parseInt(textOf("tile-open-requests")))
                .as("AC-05.3: the open-request count must include the new request")
                .isEqualTo(openBefore + 1);
        assertThat(textOf("open-request-list"))
                .as("the new request must be listed as waiting to be matched")
                .contains(patient);
    }

    @Test
    @DisplayName("AC-08.1 the dashboard reports registered and callable donors for all eight groups")
    void dashboardReportsSupplyPerGroup() {
        signInAsAdmin();

        open("/dashboard");

        var rows = driver.findElements(
                org.openqa.selenium.By.cssSelector("[data-testid^='supply-row-']"));
        assertThat(rows)
                .as("all eight blood groups must always be reported, even an empty one")
                .hasSize(8);

        int totalDonors = Integer.parseInt(textOf("tile-total-donors"));
        int callable = Integer.parseInt(textOf("tile-callable"));

        assertThat(totalDonors).as("the seeded register is not empty").isGreaterThan(0);
        assertThat(callable)
                .as("BUG-03: the callable total counts distinct people, so it can never exceed the register")
                .isLessThanOrEqualTo(totalDonors);

        // And the per-group callable figure must never exceed the number of
        // people on the register - the check that would have caught BUG-03.
        var callableCounts = driver.findElements(testId("callable-count"))
                .stream().map(e -> Integer.parseInt(e.getText().trim())).toList();
        assertThat(callableCounts).allSatisfy(count ->
                assertThat(count).isLessThanOrEqualTo(totalDonors));
    }

    @Test
    @DisplayName("the worklist is ordered by urgency severity, not alphabetically (BUG-05)")
    void worklistIsOrderedBySeverity() {
        signInAsStaff();

        open("/requests?status=NEW");

        var urgencies = driver.findElements(
                org.openqa.selenium.By.cssSelector("[data-testid^='request-row-'] td:nth-child(5)"))
                .stream().map(e -> e.getText().trim()).toList();

        // Ordering the @Enumerated(STRING) column in SQL produced Medium, High,
        // Critical - alphabetical, and backwards for the two that matter. This
        // journey is the regression test at the level a user actually sees.
        var weight = java.util.Map.of("Critical", 4, "High", 3, "Medium", 2, "Low", 1);
        var weights = urgencies.stream().map(u -> weight.getOrDefault(u, 0)).toList();

        assertThat(weights)
                .as("the worklist must run most-urgent-first: %s", urgencies)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
