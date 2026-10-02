package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.Donor;
import com.bloodbank.domain.Urgency;
import com.bloodbank.repo.BloodRequestRepository;
import com.bloodbank.repo.DonorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requirements FR-22 and FR-23: the dashboard's supply, demand and shortage flags.
 *
 * <p>The behaviour these tests exist to lock down is defect <strong>BUG-02</strong>:
 * the dashboard once had its own supply calculation that counted unavailable
 * donors as stock, so it disagreed with the shortlist the technician was looking
 * at. Several assertions below exist purely to make that regression impossible.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FR-22, FR-23 dashboard supply, demand and shortages")
class DashboardServiceTest {

    @Autowired private DashboardService dashboard;
    @Autowired private MatchingService matching;
    @Autowired private RequestService requests;
    @Autowired private DonorRepository donorRepo;
    @Autowired private BloodRequestRepository requestRepo;

    private static final Actor STAFF = Actor.of("staff", Actor.ROLE_STAFF);
    private static final Actor ADMIN = Actor.of("admin", Actor.ROLE_ADMIN);
    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void seed() {
        requestRepo.deleteAll();
        donorRepo.deleteAll();
    }

    private Donor donor(String name, BloodGroup group, String phone, LocalDate lastDonation, boolean available) {
        Donor donor = new Donor(name, group, phone, name.split(" ")[0].toLowerCase() + "@example.com", "Thane");
        donor.setLastDonationDate(lastDonation);
        donor.setAvailable(available);
        return donorRepo.save(donor);
    }

    private BloodRequest request(BloodGroup group) {
        return requests.create(new BloodRequest("Patient " + group.getLabel(), group, 1,
                                                "Civil Hospital", "Thane", Urgency.MEDIUM), STAFF);
    }

    @Test
    @DisplayName("AC-08.1: registered and callable counts are reported per blood group")
    void countsPerGroup() {
        donor("Available Rested", BloodGroup.O_NEG, "9820030001", TODAY.minusDays(200), true);
        donor("Unavailable",      BloodGroup.O_NEG, "9820030002", TODAY.minusDays(200), false);
        donor("Too Recent",       BloodGroup.O_NEG, "9820030003", TODAY.minusDays(20),  true);

        DashboardService.Snapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.donorsByGroup().get("O-")).isEqualTo(3);
        // BUG-02: only ONE of the three can actually be phoned today.
        assertThat(snapshot.callableByGroup().get("O-")).isEqualTo(1);
        assertThat(snapshot.totalDonors()).isEqualTo(3);
        assertThat(snapshot.callableDonors()).isEqualTo(1);
    }

    @Test
    @DisplayName("the callable total counts distinct people, not the sum of the overlapping per-group figures")
    void callableTotalDoesNotDoubleCount() {
        // One O- donor is callable for all eight recipient groups, so summing the
        // per-group map would report eight callable people instead of one.
        donor("Universal Donor", BloodGroup.O_NEG, "9820030071", TODAY.minusDays(200), true);
        donor("Exact B+ Donor",  BloodGroup.B_POS, "9820030072", TODAY.minusDays(200), true);

        DashboardService.Snapshot snapshot = dashboard.snapshot();

        long sumOfPerGroupFigures = snapshot.callableByGroup().values().stream()
                                            .mapToLong(Long::longValue).sum();

        assertThat(snapshot.callableDonors()).isEqualTo(2);
        assertThat(sumOfPerGroupFigures).isGreaterThan(snapshot.callableDonors());
    }

    @Test
    @DisplayName("BUG-02: the callable figure always equals the shortlist size for the same group")
    void callableFigureAgreesWithTheShortlist() {
        donor("Rested",      BloodGroup.A_POS, "9820030011", TODAY.minusDays(120), true);
        donor("Unavailable", BloodGroup.A_POS, "9820030012", TODAY.minusDays(120), false);
        donor("Recent",      BloodGroup.A_POS, "9820030013", TODAY.minusDays(5),   true);
        donor("Universal",   BloodGroup.O_NEG, "9820030014", TODAY.minusDays(120), true);

        DashboardService.Snapshot snapshot = dashboard.snapshot();

        for (BloodGroup group : BloodGroup.values()) {
            assertThat(snapshot.callableByGroup().get(group.getLabel()))
                    .as("callable count for %s must equal its shortlist size", group.getLabel())
                    .isEqualTo((long) matching.findMatches(group).size());
        }
    }

    @Test
    @DisplayName("the callable figure counts everyone who could give for that group, not only that group")
    void callableCountsCompatibleDonorsNotOnlyExactGroup() {
        // One O- donor is callable for all eight recipient groups.
        donor("Universal Donor", BloodGroup.O_NEG, "9820030021", TODAY.minusDays(200), true);

        DashboardService.Snapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.donorsByGroup().get("AB+")).isEqualTo(0);
        assertThat(snapshot.callableByGroup().get("AB+")).isEqualTo(1);
        assertThat(snapshot.callableByGroup().values()).allMatch(count -> count == 1);
    }

    @Test
    @DisplayName("AC-08.2: requests are counted per status and all four statuses are present")
    void countsPerStatus() {
        Donor compatible = donor("Matchable", BloodGroup.O_NEG, "9820030031", TODAY.minusDays(200), true);
        request(BloodGroup.B_POS);
        BloodRequest toMatch = request(BloodGroup.O_POS);
        BloodRequest toReject = request(BloodGroup.A_POS);
        requests.assignDonor(toMatch.getId(), compatible.getId(), STAFF);
        requests.reject(toReject.getId(), "Duplicate request", ADMIN);

        DashboardService.Snapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.requestsByStatus())
                .containsEntry("NEW", 1L)
                .containsEntry("MATCHED", 1L)
                .containsEntry("FULFILLED", 0L)
                .containsEntry("REJECTED", 1L);
        assertThat(snapshot.requestsByStatus()).hasSize(4);
        assertThat(snapshot.openRequests()).isEqualTo(2);   // NEW + MATCHED
    }

    @Test
    @DisplayName("AC-08.3: a group with an open request and no callable donor is flagged as a shortage")
    void shortageIsFlagged() {
        // AB- can receive only from AB-, B-, A- and O-. The only such donor is
        // unavailable, so demand exists with no supply.
        donor("Unavailable AB-", BloodGroup.AB_NEG, "9820030041", TODAY.minusDays(300), false);
        request(BloodGroup.AB_NEG);

        DashboardService.Snapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.shortages()).containsExactly("AB-");
        assertThat(snapshot.hasShortages()).isTrue();
    }

    @Test
    @DisplayName("a group with no open request is never a shortage, however empty the register")
    void noDemandIsNotAShortage() {
        // Nobody at all is registered, but nobody is asking either.
        DashboardService.Snapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.shortages()).isEmpty();
        assertThat(snapshot.hasShortages()).isFalse();
    }

    @Test
    @DisplayName("supplying a compatible donor clears the shortage without registering that exact group")
    void compatibleDonorClearsTheShortage() {
        request(BloodGroup.AB_NEG);
        assertThat(dashboard.snapshot().shortages()).containsExactly("AB-");

        // O- is compatible with AB-, so one universal donor resolves the shortage.
        donor("Universal Donor", BloodGroup.O_NEG, "9820030051", TODAY.minusDays(200), true);

        assertThat(dashboard.snapshot().shortages()).isEmpty();
    }

    @Test
    @DisplayName("a closed request no longer counts as demand, so it cannot keep a shortage alive")
    void closedRequestsAreNotDemand() {
        BloodRequest request = request(BloodGroup.AB_NEG);
        assertThat(dashboard.snapshot().shortages()).containsExactly("AB-");

        requests.reject(request.getId(), "Patient discharged", ADMIN);

        assertThat(dashboard.snapshot().shortages()).isEmpty();
    }

    @Test
    @DisplayName("fulfilling a request reduces callable supply, because the donor becomes ineligible")
    void fulfillingReducesSupply() {
        Donor donor = donor("Only Donor", BloodGroup.O_NEG, "9820030061", TODAY.minusDays(200), true);
        BloodRequest request = request(BloodGroup.O_NEG);
        requests.assignDonor(request.getId(), donor.getId(), STAFF);
        assertThat(dashboard.snapshot().callableByGroup().get("O-")).isEqualTo(1);

        requests.fulfil(request.getId(), ADMIN);

        // The donation starts a 90-day window, so supply drops to zero.
        assertThat(dashboard.snapshot().callableByGroup().get("O-")).isEqualTo(0);
        assertThat(dashboard.snapshot().requestsByStatus()).containsEntry("FULFILLED", 1L);
    }

    @Test
    @DisplayName("all eight blood groups appear in the snapshot even when the register is empty")
    void everyGroupIsAlwaysReported() {
        DashboardService.Snapshot snapshot = dashboard.snapshot();

        assertThat(snapshot.donorsByGroup()).hasSize(8);
        assertThat(snapshot.callableByGroup()).hasSize(8);
        assertThat(snapshot.donorsByGroup().values()).allMatch(count -> count == 0);
    }
}
