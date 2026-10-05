package com.bloodbank.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 1 — Staff registers a donor, and the record is usable afterwards.
 *
 * <p><strong>Why this journey is critical:</strong> if a donor cannot be added,
 * nothing else in the portal has anything to work with. It is also the only
 * journey that writes a record the other journeys can rely on existing.
 *
 * <p>Covers AC-01.1 (a valid donor is saved and appears in the list), AC-01.3
 * (a phone number that is not ten digits is rejected) and AC-03.1 (an update
 * persists). The validation case is included deliberately: a journey that only
 * walks the happy path proves the form submits, not that it is correct.
 */
@DisplayName("J1 - Staff registers, finds and updates a donor")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class J1DonorRegistrationIT extends BaseJourney {

    @Test
    @Order(1)
    @DisplayName("AC-01.1 a valid donor is saved and is then findable in the registry")
    void registersAValidDonor() {
        String suffix = unique();
        String name = "Journey Donor " + suffix;
        String phone = uniquePhone();

        signInAsStaff();

        open("/donors");
        int before = findAll("donor-name").size();

        click("register-donor");
        type("field-fullName", name);
        select("field-bloodGroup", "O-");
        type("field-phone", phone);
        type("field-email", "journey" + suffix + "@example.com");
        type("field-city", "Thane");
        check("field-available", true);
        click("save-donor");

        // The flash message is the application telling us it saved, which is a
        // stronger assertion than merely landing back on the list page.
        waitForText("flash-message", name);

        assertThat(findAll("donor-name").size())
                .as("the registry should contain one more donor than before")
                .isEqualTo(before + 1);

        // And it must be findable by the filters a technician would actually use.
        open("/donors?group=O-&city=Thane&availableOnly=true");
        assertThat(findAll("donor-name").stream().map(e -> e.getText().trim()).toList())
                .as("the new O- donor in Thane must appear in a filtered search")
                .contains(name);
    }

    @Test
    @Order(2)
    @DisplayName("AC-01.3 a phone number that is not exactly ten digits is rejected and nothing is saved")
    void rejectsAnInvalidPhoneNumber() {
        signInAsStaff();

        open("/donors");
        int before = findAll("donor-name").size();

        click("register-donor");
        type("field-fullName", "Invalid Phone Donor");
        select("field-bloodGroup", "A+");
        type("field-phone", "12345");            // five digits
        type("field-email", "invalid@example.com");
        type("field-city", "Mumbai");
        click("save-donor");

        // The form must come back with a field-level message, not a 500 and not
        // a silent save.
        //
        // waitForText, not isPresent: isPresent asks the DOM once, and clicking
        // Save is a full page navigation, so a bare isPresent races the render
        // and fails intermittently even though the page is correct. Every
        // assertion that follows a navigation has to wait for the thing it is
        // about to assert on.
        waitForText("form-errors", "exactly 10 digits");
        assertThat(textOf("form-errors"))
                .as("the form should redisplay with a field-level validation message")
                .containsIgnoringCase("exactly 10 digits");

        open("/donors");
        assertThat(findAll("donor-name").size())
                .as("a rejected form must not have created a donor")
                .isEqualTo(before);
    }

    @Test
    @Order(3)
    @DisplayName("AC-03.1 updating a donor's city persists and is visible on the detail page")
    void updatesADonor() {
        String suffix = unique();
        String name = "Update Target " + suffix;

        signInAsStaff();

        // arrange: a donor of our own, so the journey never mutates seed data
        // that another journey asserts against. Sign-in lands on the dashboard,
        // so the registry has to be opened before its buttons exist.
        open("/donors");
        click("register-donor");
        type("field-fullName", name);
        select("field-bloodGroup", "B+");
        type("field-phone", uniquePhone());
        type("field-email", "update" + suffix + "@example.com");
        type("field-city", "Pune");
        click("save-donor");
        waitForText("flash-message", name);

        // act
        open("/donors?city=Pune");
        findAll("donor-name").stream()
                .filter(e -> e.getText().trim().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the donor just created was not listed"))
                .click();
        click("edit-donor");
        type("field-city", "Navi Mumbai");
        click("save-donor");

        // assert
        waitForText("flash-message", "updated");
        assertThat(textOf("donor-detail-city"))
                .as("the city change must have persisted")
                .isEqualTo("Navi Mumbai");
    }
}
