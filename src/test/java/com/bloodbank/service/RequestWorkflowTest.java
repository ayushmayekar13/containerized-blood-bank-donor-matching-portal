package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.Donor;
import com.bloodbank.domain.RequestStatus;
import com.bloodbank.domain.Urgency;
import com.bloodbank.repo.BloodRequestRepository;
import com.bloodbank.repo.DonorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Requirements FR-17 to FR-21: the role-based request status workflow.
 *
 * <p>This is the suite that proves the two separate guards. An illegal transition
 * is <strong>409</strong>; a forbidden role is <strong>403</strong>; and Staff
 * attempting {@code NEW → FULFILLED} is both, with the role checked first.
 *
 * <p>The transition Staff must not be able to perform is also the defect
 * deliberately re-introduced in Task 10 to prove the Selenium quality gate stops a
 * deployment, so these assertions are load-bearing twice over.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FR-17..21 role-based request workflow")
class RequestWorkflowTest {

    @Autowired private RequestService service;
    @Autowired private BloodRequestRepository requests;
    @Autowired private DonorRepository donors;

    private static final Actor ADMIN = Actor.of("admin", Actor.ROLE_ADMIN);
    private static final Actor STAFF = Actor.of("staff", Actor.ROLE_STAFF);
    private static final Actor DONOR_USER = Actor.of("donor", Actor.ROLE_DONOR);

    private Donor compatibleDonor;
    private Donor ineligibleDonor;
    private Donor incompatibleDonor;

    @BeforeEach
    void seed() {
        requests.deleteAll();
        donors.deleteAll();
        LocalDate today = LocalDate.now();

        compatibleDonor   = donors.save(donor("Sneha Patil",   BloodGroup.B_POS,  "9820020001", "Thane", today.minusDays(150), true));
        ineligibleDonor   = donors.save(donor("Zoya Qureshi",  BloodGroup.B_POS,  "9820020002", "Thane", today.minusDays(10),  true));
        incompatibleDonor = donors.save(donor("Arjun Mehta",   BloodGroup.A_POS,  "9820020003", "Thane", today.minusDays(150), true));
    }

    private static Donor donor(String name, BloodGroup group, String phone, String city,
                               LocalDate lastDonation, boolean available) {
        Donor donor = new Donor(name, group, phone, name.split(" ")[0].toLowerCase() + "@example.com", city);
        donor.setLastDonationDate(lastDonation);
        donor.setAvailable(available);
        return donor;
    }

    private BloodRequest newRequest() {
        return service.create(new BloodRequest("Meera Joshi", BloodGroup.B_POS, 2,
                                               "Civil Hospital", "Thane", Urgency.HIGH), STAFF);
    }

    private BloodRequest matchedRequest() {
        BloodRequest request = newRequest();
        return service.assignDonor(request.getId(), compatibleDonor.getId(), STAFF);
    }

    // ----------------------------------------------------------------- creation

    @Nested
    @DisplayName("FR-11 creation")
    class Creation {

        @Test
        @DisplayName("AC-05.1: a new request starts in NEW with its creator and timestamp recorded")
        void startsInNew() {
            BloodRequest request = newRequest();

            assertThat(request.getStatus()).isEqualTo(RequestStatus.NEW);
            assertThat(request.getCreatedBy()).isEqualTo("staff");
            assertThat(request.getCreatedAt()).isNotNull();
            assertThat(request.getMatchedDonor()).isNull();
            assertThat(request.isOpen()).isTrue();
        }

        @Test
        @DisplayName("a DONOR account may not record a request")
        void donorCannotCreate() {
            assertThatExceptionOfType(RequestService.InsufficientRoleException.class)
                    .isThrownBy(() -> service.create(
                            new BloodRequest("X", BloodGroup.O_POS, 1, "Hospital", "Thane", Urgency.LOW),
                            DONOR_USER));
        }
    }

    // --------------------------------------------------------- legal transitions

    @Nested
    @DisplayName("FR-17 the four legal transitions")
    class LegalTransitions {

        @Test
        @DisplayName("AC-07.1: Staff may move NEW to MATCHED, recording the donor")
        void staffMayMatch() {
            BloodRequest request = newRequest();

            BloodRequest matched = service.assignDonor(request.getId(), compatibleDonor.getId(), STAFF);

            assertThat(matched.getStatus()).isEqualTo(RequestStatus.MATCHED);
            assertThat(matched.getMatchedDonor().getFullName()).isEqualTo("Sneha Patil");
            assertThat(matched.getLastModifiedBy()).isEqualTo("staff");
        }

        @Test
        @DisplayName("an Admin may also match, because Admin can do everything Staff can")
        void adminMayAlsoMatch() {
            BloodRequest request = newRequest();

            assertThat(service.assignDonor(request.getId(), compatibleDonor.getId(), ADMIN).getStatus())
                    .isEqualTo(RequestStatus.MATCHED);
        }

        @Test
        @DisplayName("AC-07.2: Admin may move MATCHED to FULFILLED, and the donor becomes ineligible for 90 days")
        void adminMayFulfilAndTheDonorBecomesIneligible() {
            BloodRequest request = matchedRequest();
            assertThat(compatibleDonor.isEligible(LocalDate.now())).isTrue();

            BloodRequest fulfilled = service.fulfil(request.getId(), ADMIN);

            assertThat(fulfilled.getStatus()).isEqualTo(RequestStatus.FULFILLED);
            assertThat(fulfilled.getLastModifiedBy()).isEqualTo("admin");

            // FR-20: fulfilling the request stamps the donation, in the same transaction
            Donor reloaded = donors.findById(compatibleDonor.getId()).orElseThrow();
            assertThat(reloaded.getLastDonationDate()).isEqualTo(LocalDate.now());
            assertThat(reloaded.isEligible(LocalDate.now())).isFalse();
        }

        @Test
        @DisplayName("AC-07.3: Admin may reject a NEW request with a reason")
        void adminMayRejectNew() {
            BloodRequest request = newRequest();

            BloodRequest rejected = service.reject(request.getId(), "Patient transferred to another hospital", ADMIN);

            assertThat(rejected.getStatus()).isEqualTo(RequestStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("Patient transferred to another hospital");
        }

        @Test
        @DisplayName("Admin may reject a MATCHED request too")
        void adminMayRejectMatched() {
            BloodRequest request = matchedRequest();

            assertThat(service.reject(request.getId(), "Donor withdrew consent", ADMIN).getStatus())
                    .isEqualTo(RequestStatus.REJECTED);
        }
    }

    // ------------------------------------------------------- illegal transitions

    @Nested
    @DisplayName("FR-18 illegal transitions are refused with 409")
    class IllegalTransitions {

        @Test
        @DisplayName("Admin cannot fulfil a NEW request - a donation cannot happen before a donor is assigned")
        void cannotFulfilWithoutMatching() {
            BloodRequest request = newRequest();

            assertThatExceptionOfType(RequestService.IllegalTransitionException.class)
                    .isThrownBy(() -> service.fulfil(request.getId(), ADMIN))
                    .withMessageContaining("cannot move from NEW to FULFILLED")
                    .withMessageContaining("legal next states are [MATCHED, REJECTED]");
        }

        @Test
        @DisplayName("AC-07.5: FULFILLED is terminal")
        void fulfilledIsTerminal() {
            BloodRequest request = matchedRequest();
            service.fulfil(request.getId(), ADMIN);

            assertThatExceptionOfType(RequestService.IllegalTransitionException.class)
                    .isThrownBy(() -> service.reject(request.getId(), "changed my mind", ADMIN))
                    .withMessageContaining("terminal state");
            assertThatExceptionOfType(RequestService.IllegalTransitionException.class)
                    .isThrownBy(() -> service.assignDonor(request.getId(), compatibleDonor.getId(), ADMIN));
        }

        @Test
        @DisplayName("AC-07.5: REJECTED is terminal")
        void rejectedIsTerminal() {
            BloodRequest request = newRequest();
            service.reject(request.getId(), "Duplicate request", ADMIN);

            assertThatExceptionOfType(RequestService.IllegalTransitionException.class)
                    .isThrownBy(() -> service.assignDonor(request.getId(), compatibleDonor.getId(), STAFF))
                    .withMessageContaining("terminal state");
        }

        @Test
        @DisplayName("a request already MATCHED cannot be matched again to a different donor")
        void cannotRematch() {
            BloodRequest request = matchedRequest();

            assertThatExceptionOfType(RequestService.IllegalTransitionException.class)
                    .isThrownBy(() -> service.assignDonor(request.getId(), ineligibleDonor.getId(), STAFF));
        }

        @Test
        @DisplayName("an unknown request id is reported as not found, surfacing as 404")
        void unknownRequestIsNotFound() {
            assertThatExceptionOfType(RequestService.RequestNotFoundException.class)
                    .isThrownBy(() -> service.fulfil(999_999L, ADMIN));
        }
    }

    // ------------------------------------------------------------ role guards

    @Nested
    @DisplayName("FR-17 role guards refuse with 403")
    class RoleGuards {

        @Test
        @DisplayName("AC-07.4: Staff may NOT fulfil - this is the defect re-introduced in Task 10")
        void staffMayNotFulfil() {
            BloodRequest request = matchedRequest();

            assertThatExceptionOfType(RequestService.InsufficientRoleException.class)
                    .isThrownBy(() -> service.fulfil(request.getId(), STAFF))
                    .withMessageContaining("requires ADMIN");

            // and nothing was changed by the refused attempt
            assertThat(service.findById(request.getId()).getStatus()).isEqualTo(RequestStatus.MATCHED);
        }

        @Test
        @DisplayName("Staff may NOT reject")
        void staffMayNotReject() {
            BloodRequest request = newRequest();

            assertThatExceptionOfType(RequestService.InsufficientRoleException.class)
                    .isThrownBy(() -> service.reject(request.getId(), "no stock", STAFF))
                    .withMessageContaining("requires ADMIN");
        }

        @Test
        @DisplayName("AC-07.4: Staff attempting NEW to FULFILLED is refused on the role, which is checked first")
        void roleIsCheckedBeforeTransitionLegality() {
            // Both guards would refuse this. The role check runs first, because
            // telling a user a transition is impossible when they were never
            // allowed to attempt it is misleading.
            BloodRequest request = newRequest();

            assertThatExceptionOfType(RequestService.InsufficientRoleException.class)
                    .isThrownBy(() -> service.fulfil(request.getId(), STAFF));
        }

        @ParameterizedTest(name = "a DONOR account cannot perform {0}")
        @CsvSource({"match", "fulfil", "reject"})
        @DisplayName("AC-07.6: a DONOR account can perform no transition at all")
        void donorCanDoNothing(String operation) {
            BloodRequest request = newRequest();

            assertThatExceptionOfType(RequestService.InsufficientRoleException.class)
                    .isThrownBy(() -> {
                        switch (operation) {
                            case "match"  -> service.assignDonor(request.getId(), compatibleDonor.getId(), DONOR_USER);
                            case "fulfil" -> service.fulfil(request.getId(), DONOR_USER);
                            default       -> service.reject(request.getId(), "reason", DONOR_USER);
                        }
                    });
        }

        @Test
        @DisplayName("an actor with no roles can perform nothing")
        void noRolesCanDoNothing() {
            Actor anonymous = new Actor("nobody", java.util.Set.of());
            BloodRequest request = newRequest();

            assertThatExceptionOfType(RequestService.InsufficientRoleException.class)
                    .isThrownBy(() -> service.assignDonor(request.getId(), compatibleDonor.getId(), anonymous));
        }
    }

    // ------------------------------------------------------------ match validity

    @Nested
    @DisplayName("the assigned donor must genuinely be a valid match (422)")
    class MatchValidity {

        @Test
        @DisplayName("an incompatible donor is refused")
        void incompatibleDonorIsRefused() {
            BloodRequest request = newRequest();   // B+ patient

            assertThatExceptionOfType(RequestService.InvalidMatchException.class)
                    .isThrownBy(() -> service.assignDonor(request.getId(), incompatibleDonor.getId(), STAFF))
                    .withMessageContaining("not a valid match");
        }

        @Test
        @DisplayName("a compatible but ineligible donor is refused, so the API cannot bypass the 90-day rule")
        void ineligibleDonorIsRefused() {
            BloodRequest request = newRequest();

            assertThatExceptionOfType(RequestService.InvalidMatchException.class)
                    .isThrownBy(() -> service.assignDonor(request.getId(), ineligibleDonor.getId(), STAFF))
                    .withMessageContaining("90-day interval");
        }

        @Test
        @DisplayName("an unavailable donor is refused")
        void unavailableDonorIsRefused() {
            compatibleDonor.setAvailable(false);
            donors.save(compatibleDonor);
            BloodRequest request = newRequest();

            assertThatExceptionOfType(RequestService.InvalidMatchException.class)
                    .isThrownBy(() -> service.assignDonor(request.getId(), compatibleDonor.getId(), STAFF));
        }
    }

    // ----------------------------------------------------------- state machine

    @Nested
    @DisplayName("the state machine on the enum")
    class StateMachine {

        @ParameterizedTest
        @EnumSource(RequestStatus.class)
        @DisplayName("a status never permits a transition to itself")
        void noSelfTransitions(RequestStatus status) {
            assertThat(status.canMoveTo(status)).isFalse();
        }

        @Test
        @DisplayName("exactly two states are terminal, and they are the two closed states")
        void terminalStates() {
            assertThat(RequestStatus.NEW.isTerminal()).isFalse();
            assertThat(RequestStatus.MATCHED.isTerminal()).isFalse();
            assertThat(RequestStatus.FULFILLED.isTerminal()).isTrue();
            assertThat(RequestStatus.REJECTED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("allowedNext drives the UI buttons, so it must be exactly the legal set")
        void allowedNext() {
            assertThat(RequestStatus.NEW.allowedNext())
                    .containsExactly(RequestStatus.MATCHED, RequestStatus.REJECTED);
            assertThat(RequestStatus.MATCHED.allowedNext())
                    .containsExactly(RequestStatus.FULFILLED, RequestStatus.REJECTED);
            assertThat(RequestStatus.FULFILLED.allowedNext()).isEmpty();
            assertThat(RequestStatus.REJECTED.allowedNext()).isEmpty();
        }

        @Test
        @DisplayName("open states are NEW and MATCHED")
        void openStates() {
            assertThat(RequestStatus.openStates())
                    .containsExactlyInAnyOrder(RequestStatus.NEW, RequestStatus.MATCHED);
        }

        @Test
        @DisplayName("a null target is never a legal transition")
        void nullTargetIsRefused() {
            assertThat(RequestStatus.NEW.canMoveTo(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("FR-21 rejection reason is mandatory")
    class RejectionReason {

        @Test
        @DisplayName("rejecting without a reason is refused, so the audit trail is never empty")
        void reasonIsRequired() {
            BloodRequest request = newRequest();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.reject(request.getId(), "   ", ADMIN));
            assertThat(service.findById(request.getId()).getStatus()).isEqualTo(RequestStatus.NEW);
        }
    }

    @Nested
    @DisplayName("FR-12 search")
    class Search {

        @Test
        @DisplayName("status and group filters combine with AND, and blanks are ignored")
        void searchFilters() {
            BloodRequest open = newRequest();
            BloodRequest closed = newRequest();
            service.reject(closed.getId(), "Duplicate", ADMIN);

            assertThat(service.search(null, null)).hasSize(2);
            assertThat(service.search("NEW", null)).extracting(BloodRequest::getId).containsExactly(open.getId());
            assertThat(service.search("REJECTED", "B+")).extracting(BloodRequest::getId).containsExactly(closed.getId());
            assertThat(service.search("NEW", "O-")).isEmpty();
            assertThat(service.search("", "  ")).hasSize(2);
        }

        @Test
        @DisplayName("BUG-05: the worklist is ordered by urgency severity, not by the alphabet")
        void worklistIsOrderedBySeverity() {
            // The urgency column is stored as text, so ordering it in SQL gives
            // MEDIUM, HIGH, CRITICAL - alphabetical, and backwards for the two
            // that matter. The ordering must come from Urgency.getWeight().
            service.create(new BloodRequest("Medium Patient", BloodGroup.B_POS, 1,
                    "Hospital", "Thane", com.bloodbank.domain.Urgency.MEDIUM), STAFF);
            service.create(new BloodRequest("Critical Patient", BloodGroup.B_POS, 1,
                    "Hospital", "Thane", com.bloodbank.domain.Urgency.CRITICAL), STAFF);
            service.create(new BloodRequest("Low Patient", BloodGroup.B_POS, 1,
                    "Hospital", "Thane", com.bloodbank.domain.Urgency.LOW), STAFF);
            service.create(new BloodRequest("High Patient", BloodGroup.B_POS, 1,
                    "Hospital", "Thane", com.bloodbank.domain.Urgency.HIGH), STAFF);

            assertThat(service.findAll())
                    .extracting(BloodRequest::getPatientName)
                    .containsExactly("Critical Patient", "High Patient",
                                     "Medium Patient", "Low Patient");
        }

        @Test
        @DisplayName("the shortlist for a request uses the request's own group and city")
        void matchesForRequest() {
            BloodRequest request = newRequest();   // B+ in Thane

            assertThat(service.matchesFor(request.getId()))
                    .extracting(MatchingService.Match::fullName)
                    .containsExactly("Sneha Patil");   // the only compatible, eligible, available donor
        }
    }
}
