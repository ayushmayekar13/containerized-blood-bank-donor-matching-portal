package com.bloodbank.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 5 — Admin confirms a donation, and the consequence reaches the donor.
 *
 * <p><strong>Why this journey is critical:</strong> it is the only journey that
 * asserts a side effect crossing from one entity to another. Fulfilling a
 * request has to make the matched donor ineligible for ninety days — if it does
 * not, the portal will keep recommending someone who has just given blood, which
 * is the single worst thing it could do and the exact failure the manual register
 * is criticised for in Task 1.
 *
 * <p>Covers AC-07.2 (Admin may fulfil, and the donation date is stamped),
 * AC-07.5 (`FULFILLED` is terminal) and AC-03.2 / FR-20 (the donor is excluded
 * from shortlists immediately afterwards).
 */
@DisplayName("J5 - Admin fulfils a request and the donor becomes ineligible")
class J5FulfilAndEligibilityIT extends BaseJourney {

    @Test
    @DisplayName("AC-07.2 / FR-20 fulfilling stamps the donation and the donor stops being callable")
    void fulfillingMakesTheDonorIneligible() {
        signInAsAdmin();

        // ---- arrange: a fresh request, matched to the top-ranked donor -------
        String patient = "Fulfil Target " + unique();
        open("/requests/new");
        type("field-patientName", patient);
        select("field-request-bloodGroup", "B+");
        type("field-unitsNeeded", "1");
        type("field-hospital", "Civil Hospital");
        type("field-request-city", "Thane");
        select("field-urgency", "High");
        click("save-request");
        waitForText("request-detail-patient", patient);

        String donorName = textOf("match-name");
        String donorUrl = driver.findElement(testId("match-name")).getAttribute("href");

        click("assign-donor-1");
        waitForText("request-detail-status", "Matched");

        // The donor must be callable right now - otherwise the assertion after
        // fulfilment would prove nothing.
        driver.get(donorUrl);
        assertThat(textOf("eligibility"))
                .as("the matched donor must be eligible before the donation is recorded")
                .isEqualTo("Eligible");
        driver.navigate().back();
        driver.navigate().back();

        // ---- act -----------------------------------------------------------
        open("/requests?status=MATCHED&group=B%2B");
        driver.findElements(testId("request-patient")).stream()
                .filter(e -> e.getText().trim().equals(patient))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the matched request was not listed"))
                .click();
        waitForText("request-detail-status", "Matched");
        click("fulfil-request");

        // ---- assert: the request ------------------------------------------
        waitForText("flash-message", "Donation confirmed");
        assertThat(textOf("request-detail-status"))
                .as("AC-07.2: an Admin confirming a donation moves the request to Fulfilled")
                .isEqualTo("Fulfilled");
        assertThat(textOf("modified-by")).isEqualTo("admin");

        // AC-07.5: Fulfilled is terminal - no further action may be offered.
        assertThat(isPresent("no-actions"))
                .as("a terminal state must say so instead of offering actions")
                .isTrue();
        assertThat(textOf("no-actions")).containsIgnoringCase("terminal state");
        assertThat(isPresent("fulfil-request")).isFalse();
        assertThat(isPresent("reject-request")).isFalse();

        // ---- assert: the side effect on the donor --------------------------
        driver.get(donorUrl);
        assertThat(textOf("donor-detail-lastdonation"))
                .as("FR-20: the donation date must have been stamped on the donor")
                .doesNotContainIgnoringCase("never donated");
        assertThat(textOf("eligibility"))
                .as("FR-20: the donor must now show a future eligibility date, not 'Eligible'")
                .startsWith("Eligible on");

        // ---- assert: and therefore the engine no longer offers them ---------
        open("/match?group=B%2B&city=Thane");
        var shortlisted = driver.findElements(testId("match-name"))
                .stream().map(e -> e.getText().trim()).toList();
        assertThat(shortlisted)
                .as("the donor who just gave blood must not be recommended again")
                .doesNotContain(donorName);
    }

    @Test
    @DisplayName("AC-07.3 / FR-21 Admin rejects a request with a mandatory reason, and the reason is shown")
    void adminRejectsWithAReason() {
        signInAsAdmin();

        String patient = "Reject Target " + unique();
        open("/requests/new");
        type("field-patientName", patient);
        select("field-request-bloodGroup", "A+");
        type("field-unitsNeeded", "1");
        type("field-hospital", "Sion Hospital");
        type("field-request-city", "Mumbai");
        select("field-urgency", "Low");
        click("save-request");
        waitForText("request-detail-patient", patient);

        type("reject-reason", "Patient transferred to another hospital");
        click("reject-request");

        waitForText("request-detail-status", "Rejected");
        assertThat(textOf("rejection-reason"))
                .as("FR-21: the recorded reason must be visible, so the audit trail is never empty")
                .isEqualTo("Patient transferred to another hospital");
        assertThat(isPresent("no-actions"))
                .as("AC-07.5: Rejected is terminal too")
                .isTrue();
    }

    @Test
    @DisplayName("AC-04.1 a Donor sees only their own record, and can turn their availability off")
    void donorControlsOwnAvailability() {
        signInAsDonor();

        // A donor must not be offered the registry or the worklist at all.
        assertThat(isPresent("nav-donors"))
                .as("AC-04.1: a DONOR must not be offered the donor registry")
                .isFalse();
        assertThat(isPresent("nav-requests")).isFalse();
        assertThat(isPresent("nav-me")).isTrue();

        open("/me");
        assertThat(isPresent("my-name")).isTrue();

        boolean availableBefore = isCurrentlyAvailable();

        // Toggle, then toggle back, each from a freshly loaded page.
        //
        // Both details matter. Asserting on the persistent state rather than on
        // the flash message, because a flash attribute is consumed by the first
        // render that shows it - asserting on it makes the test depend on
        // exactly one page load happening, which is a property of the browser's
        // timing and not of the application. And reloading /me before the second
        // click, because clicking twice in a row can land the second click on
        // the DOM of the page that is already being replaced, which submits the
        // previous hidden value and silently does nothing.
        toggleAvailability(!availableBefore);
        assertThat(isCurrentlyAvailable())
                .as("toggling availability must change what the page reports")
                .isNotEqualTo(availableBefore);

        // Restore, so the journey leaves the deployed instance as it found it -
        // which is what makes the suite re-runnable against a long-lived
        // environment rather than only against a freshly seeded one.
        toggleAvailability(availableBefore);
        assertThat(isCurrentlyAvailable())
                .as("the journey must restore the state it found")
                .isEqualTo(availableBefore);
    }

    private boolean isCurrentlyAvailable() {
        return textOf("availability-state").contains("currently available");
    }

    /** Clicks the toggle from a freshly loaded page and waits for the state it should produce. */
    private void toggleAvailability(boolean targetAvailable) {
        open("/me");
        clickSubmit("toggle-availability");
        waitForText("availability-state",
                    targetAvailable ? "currently available" : "currently unavailable");
    }
}
