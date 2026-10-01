package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import com.bloodbank.repo.DonorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Requirements FR-01 to FR-06: donor registry create, view, update and search.
 *
 * <p>Runs against the real repository on an in-memory database, because the
 * search query itself — the AND-combination of optional filters — is the thing
 * being verified, and a mocked repository would verify nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FR-01..06 donor registry service")
class DonorServiceTest {

    @Autowired private DonorService service;
    @Autowired private DonorRepository repository;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
            donor("Rohit Deshmukh", BloodGroup.O_NEG,  "9820010001", "Thane",  TODAY.minusDays(160), true),
            donor("Sneha Patil",    BloodGroup.B_POS,  "9820010002", "Thane",  TODAY.minusDays(142), true),
            donor("Imran Shaikh",   BloodGroup.O_POS,  "9820010003", "Pune",   null,                 true),
            donor("Priya Nair",     BloodGroup.A_NEG,  "9820010004", "Thane",  TODAY.minusDays(45),  true),
            donor("Fatima Ansari",  BloodGroup.AB_NEG, "9820010005", "Mumbai", TODAY.minusDays(300), false)
        ));
    }

    private static Donor donor(String name, BloodGroup group, String phone, String city,
                               LocalDate lastDonation, boolean available) {
        Donor donor = new Donor(name, group, phone, name.split(" ")[0].toLowerCase() + "@example.com", city);
        donor.setLastDonationDate(lastDonation);
        donor.setAvailable(available);
        return donor;
    }

    @Nested
    @DisplayName("FR-04 search: optional filters combined with logical AND")
    class Search {

        @Test
        @DisplayName("no filters returns the whole registry, ordered by name")
        void noFiltersReturnsEverything() {
            assertThat(service.search(null, null, false))
                    .extracting(Donor::getFullName)
                    .containsExactly("Fatima Ansari", "Imran Shaikh", "Priya Nair",
                                     "Rohit Deshmukh", "Sneha Patil");
        }

        @ParameterizedTest(name = "group filter {0} returns {1} donor(s)")
        @CsvSource({"O-,1", "O+,1", "B+,1", "A-,1", "AB-,1", "A+,0"})
        @DisplayName("AC-02.1: filtering by blood group returns only that group")
        void filterByGroup(String group, int expected) {
            List<Donor> found = service.search(group, null, false);

            assertThat(found).hasSize(expected);
            assertThat(found).allSatisfy(d -> assertThat(d.getBloodGroup().getLabel()).isEqualTo(group));
        }

        @Test
        @DisplayName("city filter is a case-insensitive partial match")
        void filterByCityIsCaseInsensitivePartial() {
            assertThat(service.search(null, "thane", false)).hasSize(3);
            assertThat(service.search(null, "THA", false)).hasSize(3);
            assertThat(service.search(null, "pun", false)).hasSize(1);
        }

        @Test
        @DisplayName("AC-02.2: group and city filters combine with AND, not OR")
        void filtersCombineWithAnd() {
            // 3 donors are in Thane and 1 donor is O+; an OR would return 4.
            assertThat(service.search("O+", "Thane", false)).isEmpty();
            assertThat(service.search("O-", "Thane", false))
                    .extracting(Donor::getFullName).containsExactly("Rohit Deshmukh");
        }

        @Test
        @DisplayName("AC-02.3: availableOnly excludes donors flagged unavailable")
        void availableOnlyExcludesUnavailable() {
            assertThat(service.search(null, null, false)).hasSize(5);
            assertThat(service.search(null, null, true)).hasSize(4);
            assertThat(service.search(null, null, true))
                    .extracting(Donor::getFullName).doesNotContain("Fatima Ansari");
        }

        @Test
        @DisplayName("blank filters are treated as absent rather than as an empty match")
        void blankFiltersAreIgnored() {
            assertThat(service.search("", "   ", false)).hasSize(5);
        }

        @Test
        @DisplayName("an unknown blood group is rejected, surfacing as HTTP 400")
        void unknownGroupIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> service.search("C+", null, false));
        }

        @Test
        @DisplayName("a search matching nothing returns an empty list, not null")
        void noMatchReturnsEmptyList() {
            assertThat(service.search("A+", "Nagpur", true)).isEmpty();
        }
    }

    @Nested
    @DisplayName("FR-01, FR-02 create")
    class Create {

        @Test
        @DisplayName("a valid donor is persisted and given an id")
        void createsDonor() {
            Donor saved = service.create(donor("Nikhil Bhosale", BloodGroup.AB_POS,
                                               "9820019999", "Navi Mumbai", null, true));

            assertThat(saved.getId()).isNotNull();
            assertThat(service.count()).isEqualTo(6);
            assertThat(service.findByPhone("9820019999")).isPresent();
        }

        @Test
        @DisplayName("a duplicate phone number is refused, surfacing as HTTP 409")
        void refusesDuplicatePhone() {
            assertThatExceptionOfType(DonorService.DuplicatePhoneException.class)
                    .isThrownBy(() -> service.create(donor("Impostor", BloodGroup.O_POS,
                                                           "9820010001", "Thane", null, true)))
                    .withMessageContaining("9820010001");

            assertThat(service.count()).isEqualTo(5);
        }

        @Test
        @DisplayName("AC-01.4: a donor created with no donation date is immediately eligible")
        void newDonorWithNoHistoryIsEligible() {
            Donor saved = service.create(donor("First Timer", BloodGroup.B_NEG,
                                               "9820018888", "Pune", null, true));

            assertThat(saved.isEligible(service.today())).isTrue();
        }
    }

    @Nested
    @DisplayName("FR-06 update and availability")
    class Update {

        @Test
        @DisplayName("AC-03.1: changing the city persists")
        void updatesCity() {
            Donor existing = service.findByPhone("9820010001").orElseThrow();

            Donor changes = donor(existing.getFullName(), existing.getBloodGroup(),
                                  existing.getPhone(), "Pune", existing.getLastDonationDate(), true);
            service.update(existing.getId(), changes);

            assertThat(service.findById(existing.getId()).getCity()).isEqualTo("Pune");
        }

        @Test
        @DisplayName("keeping its own phone number on update is not treated as a duplicate")
        void keepingOwnPhoneIsAllowed() {
            Donor existing = service.findByPhone("9820010002").orElseThrow();

            Donor changes = donor("Sneha Patil-Kulkarni", existing.getBloodGroup(),
                                  existing.getPhone(), existing.getCity(),
                                  existing.getLastDonationDate(), true);

            assertThat(service.update(existing.getId(), changes).getFullName())
                    .isEqualTo("Sneha Patil-Kulkarni");
        }

        @Test
        @DisplayName("taking another donor's phone number on update is refused")
        void takingAnotherPhoneIsRefused() {
            Donor existing = service.findByPhone("9820010002").orElseThrow();

            Donor changes = donor(existing.getFullName(), existing.getBloodGroup(),
                                  "9820010001", existing.getCity(), null, true);

            assertThatExceptionOfType(DonorService.DuplicatePhoneException.class)
                    .isThrownBy(() -> service.update(existing.getId(), changes));
        }

        @Test
        @DisplayName("AC-03.2: recording a donation makes the donor ineligible from that moment")
        void recordDonationMakesIneligible() {
            Donor existing = service.findByPhone("9820010001").orElseThrow();
            assertThat(existing.isEligible(service.today())).isTrue();

            service.recordDonation(existing.getId());

            Donor reloaded = service.findById(existing.getId());
            assertThat(reloaded.getLastDonationDate()).isEqualTo(service.today());
            assertThat(reloaded.isEligible(service.today())).isFalse();
        }

        @Test
        @DisplayName("availability can be toggled independently of any other field")
        void togglesAvailability() {
            Donor existing = service.findByPhone("9820010003").orElseThrow();

            assertThat(service.setAvailability(existing.getId(), false).isAvailable()).isFalse();
            assertThat(service.setAvailability(existing.getId(), true).isAvailable()).isTrue();
        }

        @Test
        @DisplayName("an unknown id is reported as not found, surfacing as HTTP 404")
        void unknownIdIsNotFound() {
            assertThatExceptionOfType(DonorService.DonorNotFoundException.class)
                    .isThrownBy(() -> service.findById(999_999L));
        }
    }
}
