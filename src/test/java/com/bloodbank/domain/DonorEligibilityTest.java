package com.bloodbank.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requirements FR-07 and FR-08: the 90-day whole-blood donation interval.
 *
 * <p>Defect BUG-01 was an off-by-one here — the comparison was {@code >} instead of
 * {@code >=}, so a donor who last donated exactly 90 days ago was excluded. The
 * boundary is therefore tested at 89, 90 and 91 days explicitly.
 */
@DisplayName("FR-08 donor eligibility and the 90-day interval")
class DonorEligibilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private Donor donorWhoLastDonated(LocalDate date) {
        Donor donor = new Donor("Rohit Deshmukh", BloodGroup.O_NEG,
                                "9820012345", "rohit@example.com", "Thane");
        donor.setLastDonationDate(date);
        return donor;
    }

    @Test
    @DisplayName("FR-07: a donor who has never donated is eligible")
    void neverDonatedIsEligible() {
        Donor donor = donorWhoLastDonated(null);

        assertThat(donor.isEligible(TODAY)).isTrue();
        assertThat(donor.daysSinceLastDonation(TODAY)).isEqualTo(Long.MAX_VALUE);
        assertThat(donor.nextEligibleDate(TODAY)).isEqualTo(TODAY);
    }

    @ParameterizedTest(name = "last donated {0} days ago -> eligible = {1}")
    @CsvSource({
        "0,   false",   // donated today
        "1,   false",
        "45,  false",
        "89,  false",   // one day short — BUG-01 boundary
        "90,  true",    // exactly the interval — BUG-01 boundary, must be eligible
        "91,  true",    // one day past
        "120, true",
        "400, true"
    })
    @DisplayName("eligibility flips at exactly 90 days, inclusive")
    void eligibilityBoundary(long daysAgo, boolean expectedEligible) {
        Donor donor = donorWhoLastDonated(TODAY.minusDays(daysAgo));

        assertThat(donor.isEligible(TODAY))
                .as("donor who last donated %d days ago", daysAgo)
                .isEqualTo(expectedEligible);
        assertThat(donor.daysSinceLastDonation(TODAY)).isEqualTo(daysAgo);
    }

    @Test
    @DisplayName("the donation interval constant is 90 days")
    void intervalIsNinetyDays() {
        assertThat(Donor.DONATION_INTERVAL_DAYS).isEqualTo(90);
    }

    @Test
    @DisplayName("next eligible date is 90 days after the last donation")
    void nextEligibleDateIsNinetyDaysLater() {
        Donor donor = donorWhoLastDonated(LocalDate.of(2026, 9, 1));

        assertThat(donor.nextEligibleDate(TODAY)).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("next eligible date never reads as a past date for an already-eligible donor")
    void nextEligibleDateIsNeverInThePast() {
        Donor donor = donorWhoLastDonated(TODAY.minusDays(200));

        assertThat(donor.nextEligibleDate(TODAY)).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("an unavailable donor is not callable even when eligible")
    void unavailableDonorIsNotCallable() {
        Donor donor = donorWhoLastDonated(TODAY.minusDays(365));
        donor.setAvailable(false);

        assertThat(donor.isEligible(TODAY)).isTrue();
        assertThat(donor.isCallable(TODAY)).isFalse();
    }

    @Test
    @DisplayName("an available but ineligible donor is not callable")
    void ineligibleDonorIsNotCallable() {
        Donor donor = donorWhoLastDonated(TODAY.minusDays(30));
        donor.setAvailable(true);

        assertThat(donor.isCallable(TODAY)).isFalse();
    }

    @Test
    @DisplayName("FR-20: recording a donation makes the donor ineligible immediately")
    void recordingADonationStartsANewWindow() {
        Donor donor = donorWhoLastDonated(TODAY.minusDays(365));
        assertThat(donor.isCallable(TODAY)).isTrue();

        donor.recordDonation(TODAY);

        assertThat(donor.isEligible(TODAY)).isFalse();
        assertThat(donor.nextEligibleDate(TODAY)).isEqualTo(TODAY.plusDays(90));
    }
}
