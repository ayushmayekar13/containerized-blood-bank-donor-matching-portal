package com.bloodbank.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 4 — Staff assigns a matched donor, and Staff is refused the fulfil action.
 *
 * <p><strong>Why this journey is critical:</strong> it is the one that guards the
 * project's accountability claim. A transition anyone may perform is not a
 * workflow, and the defect deliberately re-introduced in Task 10 to prove the
 * quality gate works is precisely the one this journey catches.
 *
 * <p>Covers AC-07.1 (Staff may move `NEW → MATCHED` and the donor is recorded)
 * and AC-07.4 (Staff may not fulfil) at the level a user actually experiences —
 * the button is absent and the reason is stated, rather than the user clicking
 * and receiving an error.
 */
@DisplayName("J4 - Staff assigns a donor and is refused the fulfil action")
class J4MatchAndRoleGuardIT extends BaseJourney {

    /** Creates a fresh NEW request so the journey never depends on seed state. */
    private String createRequest(String group, String city) {
        String patient = "Match Target " + unique();
        open("/requests/new");
        type("field-patientName", patient);
        select("field-request-bloodGroup", group);
        type("field-unitsNeeded", "1");
        type("field-hospital", "Civil Hospital");
        type("field-request-city", city);
        select("field-urgency", "Medium");
        click("save-request");
        waitForText("request-detail-patient", patient);
        return patient;
    }

    @Test
    @DisplayName("AC-07.1 Staff assigns the top-ranked donor and the request becomes Matched")
    void staffAssignsTheTopRankedDonor() {
        signInAsStaff();
        createRequest("B+", "Thane");

        // The shortlist must be offered on a NEW request, with the engine's
        // ranking already applied.
        assertThat(isPresent("match-row-1"))
                .as("a NEW request must offer the ranked shortlist")
                .isTrue();
        String topDonor = textOf("match-name");

        click("assign-donor-1");

        waitForText("flash-message", "assigned");
        assertThat(textOf("request-detail-status"))
                .as("AC-07.1: assigning a donor moves the request to Matched")
                .isEqualTo("Matched");
        assertThat(textOf("matched-donor"))
                .as("FR-19: the assigned donor must be recorded against the request")
                .isEqualTo(topDonor);
        assertThat(textOf("modified-by"))
                .as("NFR-15: the transition must record who performed it")
                .isEqualTo("staff");

        // Once matched, the shortlist must no longer be offered: reassignment
        // would lose the record of who was originally matched.
        assertThat(isPresent("match-row-1"))
                .as("a matched request must not keep offering the shortlist")
                .isFalse();
    }

    @Test
    @DisplayName("AC-07.4 Staff is not offered the fulfil action, and is told why")
    void staffIsRefusedTheFulfilAction() {
        signInAsStaff();
        createRequest("B+", "Thane");
        click("assign-donor-1");
        waitForText("request-detail-status", "Matched");

        // This is the headline assertion of the whole suite. Only an ADMIN may
        // confirm that a donation took place.
        assertThat(isPresent("fulfil-request"))
                .as("AC-07.4: a STAFF user must not be offered the fulfil action")
                .isFalse();
        assertThat(isPresent("reject-request"))
                .as("rejecting is also ADMIN-only")
                .isFalse();

        // An action that silently disappears reads as a bug, so the screen
        // explains the absence.
        assertThat(isPresent("fulfil-not-permitted")).isTrue();
        assertThat(textOf("fulfil-not-permitted"))
                .containsIgnoringCase("only an admin");
    }

    @Test
    @DisplayName("an Admin, on the same request, IS offered both actions - so the absence above is the role, not a bug")
    void adminIsOfferedTheActions() {
        signInAsStaff();
        createRequest("B+", "Thane");
        click("assign-donor-1");
        waitForText("request-detail-status", "Matched");
        String url = driver.getCurrentUrl();

        signOutBetweenJourneys();
        signInAsAdmin();
        driver.get(url);

        // The same request, the same state, a different role. Asserting both
        // sides is what makes the previous test meaningful: without this, a
        // missing button could equally be a rendering failure.
        assertThat(isPresent("fulfil-request"))
                .as("an ADMIN must be offered the fulfil action on a Matched request")
                .isTrue();
        assertThat(isPresent("reject-request")).isTrue();
        assertThat(isPresent("fulfil-not-permitted")).isFalse();
    }
}
