package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import com.bloodbank.repo.DonorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Requirements FR-13 to FR-16: the matching engine.
 *
 * <p>Uses a fixed {@link Clock} so that "89 days ago" means exactly that, and a
 * stubbed repository so the engine is tested in isolation from JPA. The
 * repository returns every compatible-group donor; filtering by availability and
 * eligibility, and all ranking, is the engine's job and is what is asserted here.
 */
@DisplayName("FR-13..16 donor matching engine")
class MatchingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private DonorRepository donors;
    private MatchingService matching;
    private final List<Donor> registry = new ArrayList<>();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        registry.clear();
        nextId = 1;
        donors = mock(DonorRepository.class);
        matching = new MatchingService(donors, FIXED_CLOCK);

        // The repository contract is "give me every donor whose group is in this
        // list"; the engine applies availability, eligibility and ranking itself.
        when(donors.findByBloodGroupIn(anyList())).thenAnswer(invocation -> {
            List<BloodGroup> groups = invocation.getArgument(0);
            return registry.stream().filter(d -> groups.contains(d.getBloodGroup())).toList();
        });
    }

    private Donor register(String name, BloodGroup group, String city, Long daysSinceDonation, boolean available) {
        Donor donor = new Donor(name, group, "98200%05d".formatted(nextId), name.replace(' ', '.') + "@example.com", city);
        if (daysSinceDonation != null) {
            donor.setLastDonationDate(TODAY.minusDays(daysSinceDonation));
        }
        donor.setAvailable(available);
        // the entity id is database-generated, so it is set reflectively for the test
        try {
            var field = Donor.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(donor, nextId++);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        registry.add(donor);
        return donor;
    }

    // ------------------------------------------------------------- the predicates

    @Nested
    @DisplayName("the three predicates a match must satisfy")
    class Predicates {

        @Test
        @DisplayName("FR-14: only compatible groups appear — a B+ request draws B+, B-, O+ and O-")
        void onlyCompatibleGroupsAreShortlisted() {
            register("Sneha Patil",     BloodGroup.B_POS,  "Thane",  200L, true);
            register("Rohit Deshmukh",  BloodGroup.O_NEG,  "Thane",  200L, true);
            register("Imran Shaikh",    BloodGroup.O_POS,  "Pune",   200L, true);
            register("Kavita Rane",     BloodGroup.B_NEG,  "Mumbai", 200L, true);
            register("Arjun Mehta",     BloodGroup.A_POS,  "Thane",  200L, true);   // incompatible
            register("Nikhil Bhosale",  BloodGroup.AB_POS, "Thane",  200L, true);   // incompatible

            List<MatchingService.Match> matches = matching.findMatches(BloodGroup.B_POS, "Thane");

            assertThat(matches).extracting(MatchingService.Match::bloodGroupLabel)
                               .containsOnly("B+", "B-", "O+", "O-");
            assertThat(matches).extracting(MatchingService.Match::fullName)
                               .doesNotContain("Arjun Mehta", "Nikhil Bhosale");
        }

        @Test
        @DisplayName("FR-13: an unavailable donor is excluded even when compatible and eligible")
        void unavailableDonorsAreExcluded() {
            register("Available Donor",   BloodGroup.O_NEG, "Thane", 200L, true);
            register("Unavailable Donor", BloodGroup.O_NEG, "Thane", 200L, false);

            List<MatchingService.Match> matches = matching.findMatches(BloodGroup.O_NEG, "Thane");

            assertThat(matches).extracting(MatchingService.Match::fullName)
                               .containsExactly("Available Donor");
        }

        @Test
        @DisplayName("FR-08: a donor who donated 30 days ago is excluded as ineligible")
        void ineligibleDonorsAreExcluded() {
            register("Recent Donor", BloodGroup.O_NEG, "Thane", 30L, true);
            register("Rested Donor", BloodGroup.O_NEG, "Thane", 95L, true);

            List<MatchingService.Match> matches = matching.findMatches(BloodGroup.O_NEG, "Thane");

            assertThat(matches).extracting(MatchingService.Match::fullName)
                               .containsExactly("Rested Donor");
        }

        @Test
        @DisplayName("the eligibility boundary inside the engine is also inclusive at 90 days")
        void boundaryInsideTheEngine() {
            register("Day 89 Donor", BloodGroup.A_POS, "Pune", 89L, true);
            register("Day 90 Donor", BloodGroup.A_POS, "Pune", 90L, true);

            assertThat(matching.findMatches(BloodGroup.A_POS, "Pune"))
                    .extracting(MatchingService.Match::fullName)
                    .containsExactly("Day 90 Donor");
        }

        @Test
        @DisplayName("a donor who has never donated is a valid match")
        void neverDonatedIsMatched() {
            register("First Time Donor", BloodGroup.AB_NEG, "Mumbai", null, true);

            List<MatchingService.Match> matches = matching.findMatches(BloodGroup.AB_NEG, "Mumbai");

            assertThat(matches).hasSize(1);
            assertThat(matches.get(0).neverDonated()).isTrue();
        }
    }

    // ----------------------------------------------------------------- the ranking

    @Nested
    @DisplayName("ranking rules, in priority order")
    class Ranking {

        @Test
        @DisplayName("FR-15 rule 1: an exact group match outranks a merely compatible one, to preserve O- stock")
        void exactGroupMatchRanksFirst() {
            register("Universal Donor", BloodGroup.O_NEG, "Thane", 300L, true);
            register("Exact Match",     BloodGroup.O_POS, "Thane", 100L, true);

            List<MatchingService.Match> matches = matching.findMatches(BloodGroup.O_POS, "Thane");

            assertThat(matches).extracting(MatchingService.Match::fullName)
                               .containsExactly("Exact Match", "Universal Donor");
            assertThat(matches.get(0).exactGroupMatch()).isTrue();
            assertThat(matches.get(1).exactGroupMatch()).isFalse();
        }

        @Test
        @DisplayName("FR-15 rule 2: among equally compatible donors, the same city ranks higher")
        void sameCityRanksHigher() {
            register("Out Of Town", BloodGroup.B_POS, "Pune",  300L, true);
            register("In Town",     BloodGroup.B_POS, "Thane", 100L, true);

            List<MatchingService.Match> matches = matching.findMatches(BloodGroup.B_POS, "Thane");

            assertThat(matches).extracting(MatchingService.Match::fullName)
                               .containsExactly("In Town", "Out Of Town");
            assertThat(matches.get(0).sameCity()).isTrue();
        }

        @Test
        @DisplayName("city comparison ignores case, so 'thane' and 'Thane' are the same city")
        void cityComparisonIsCaseInsensitive() {
            register("Lower Case City", BloodGroup.B_POS, "thane", 100L, true);

            assertThat(matching.findMatches(BloodGroup.B_POS, "Thane").get(0).sameCity()).isTrue();
        }

        @Test
        @DisplayName("FR-15 rule 3: otherwise the donor who has rested longest ranks higher, rotating the load")
        void longestRestedRanksHigher() {
            register("Rested 100", BloodGroup.A_NEG, "Mumbai", 100L, true);
            register("Rested 300", BloodGroup.A_NEG, "Mumbai", 300L, true);
            register("Rested 200", BloodGroup.A_NEG, "Mumbai", 200L, true);

            assertThat(matching.findMatches(BloodGroup.A_NEG, "Mumbai"))
                    .extracting(MatchingService.Match::fullName)
                    .containsExactly("Rested 300", "Rested 200", "Rested 100");
        }

        @Test
        @DisplayName("a donor who never donated outranks one who donated recently enough to be eligible")
        void neverDonatedOutranksRecentlyEligible() {
            register("Donated Once", BloodGroup.A_NEG, "Mumbai", 95L, true);
            register("Never Donated", BloodGroup.A_NEG, "Mumbai", null, true);

            assertThat(matching.findMatches(BloodGroup.A_NEG, "Mumbai"))
                    .extracting(MatchingService.Match::fullName)
                    .containsExactly("Never Donated", "Donated Once");
        }

        @Test
        @DisplayName("ranks are 1-based and contiguous")
        void ranksAreOneBasedAndContiguous() {
            register("Donor A", BloodGroup.O_NEG, "Thane", 100L, true);
            register("Donor B", BloodGroup.O_NEG, "Thane", 200L, true);
            register("Donor C", BloodGroup.O_NEG, "Thane", 300L, true);

            assertThat(matching.findMatches(BloodGroup.O_NEG, "Thane"))
                    .extracting(MatchingService.Match::rank)
                    .containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("ordering is deterministic for otherwise identical donors, so tests cannot be flaky")
        void tieBreakOnNameIsDeterministic() {
            register("Zara Khan",  BloodGroup.AB_POS, "Pune", 100L, true);
            register("Aarav Jain", BloodGroup.AB_POS, "Pune", 100L, true);

            assertThat(matching.findMatches(BloodGroup.AB_POS, "Pune"))
                    .extracting(MatchingService.Match::fullName)
                    .containsExactly("Aarav Jain", "Zara Khan");
        }

        @Test
        @DisplayName("the full priority order holds when all three rules are in play at once")
        void fullPriorityOrder() {
            register("Compatible, same city, long rest", BloodGroup.O_NEG, "Thane", 400L, true);
            register("Exact, other city, short rest",    BloodGroup.B_POS, "Pune",   95L, true);
            register("Exact, same city, short rest",     BloodGroup.B_POS, "Thane",  95L, true);
            register("Compatible, other city, long rest",BloodGroup.O_POS, "Pune",   400L, true);

            assertThat(matching.findMatches(BloodGroup.B_POS, "Thane"))
                    .extracting(MatchingService.Match::fullName)
                    .containsExactly(
                            "Exact, same city, short rest",        // exact + same city
                            "Exact, other city, short rest",        // exact
                            "Compatible, same city, long rest",     // same city
                            "Compatible, other city, long rest");   // neither
        }
    }

    // ------------------------------------------------------------- edge behaviour

    @Nested
    @DisplayName("edge cases and the dashboard contract")
    class EdgeCases {

        @Test
        @DisplayName("an empty registry yields an empty shortlist, not an error")
        void emptyRegistry() {
            assertThat(matching.findMatches(BloodGroup.O_NEG, "Thane")).isEmpty();
        }

        @Test
        @DisplayName("a registry where nobody is callable yields an empty shortlist — the shortage case")
        void nobodyCallable() {
            register("Too Recent",  BloodGroup.O_NEG, "Thane", 10L, true);
            register("Unavailable", BloodGroup.O_NEG, "Thane", 500L, false);

            assertThat(matching.findMatches(BloodGroup.O_NEG, "Thane")).isEmpty();
        }

        @Test
        @DisplayName("a null city disables the proximity rule rather than failing")
        void nullCityIsAllowed() {
            register("Any City Donor", BloodGroup.O_NEG, "Thane", 200L, true);

            List<MatchingService.Match> matches = matching.findMatches(BloodGroup.O_NEG);

            assertThat(matches).hasSize(1);
            assertThat(matches.get(0).sameCity()).isFalse();
        }

        @Test
        @DisplayName("a null recipient group is rejected instead of silently matching everyone")
        void nullRecipientGroupIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> matching.findMatches(null, "Thane"));
        }

        @Test
        @DisplayName("FR-23: countMatches uses the same predicate as findMatches, so the dashboard cannot disagree with the shortlist")
        void countMatchesAgreesWithFindMatches() {
            // This is defect BUG-02: the dashboard counted unavailable donors as supply.
            register("Callable",    BloodGroup.A_POS, "Pune", 200L, true);
            register("Unavailable", BloodGroup.A_POS, "Pune", 200L, false);
            register("Ineligible",  BloodGroup.A_POS, "Pune", 10L,  true);

            assertThat(matching.countMatches(BloodGroup.A_POS)).isEqualTo(1);
            assertThat(matching.countMatches(BloodGroup.A_POS))
                    .isEqualTo(matching.findMatches(BloodGroup.A_POS).size());
        }

        @Test
        @DisplayName("isValidMatch guards a transition: it rejects an incompatible or uncallable donor")
        void isValidMatchGuardsTransitions() {
            Donor compatible   = register("Compatible",   BloodGroup.O_NEG, "Thane", 200L, true);
            Donor incompatible = register("Incompatible", BloodGroup.A_POS, "Thane", 200L, true);
            Donor ineligible   = register("Ineligible",   BloodGroup.O_NEG, "Thane", 10L,  true);

            assertThat(matching.isValidMatch(BloodGroup.O_POS, compatible)).isTrue();
            assertThat(matching.isValidMatch(BloodGroup.O_POS, incompatible)).isFalse();
            assertThat(matching.isValidMatch(BloodGroup.O_POS, ineligible)).isFalse();
        }
    }

    @Test
    @DisplayName("NFR-01: a shortlist over 500 donors is produced in well under two seconds")
    void performanceOverFiveHundredDonors() {
        BloodGroup[] groups = BloodGroup.values();
        String[] cities = {"Mumbai", "Thane", "Navi Mumbai", "Pune"};
        for (int i = 0; i < 500; i++) {
            register("Donor %03d".formatted(i), groups[i % groups.length], cities[i % cities.length],
                     (long) (i % 365), true);
        }

        long startedAt = System.nanoTime();
        List<MatchingService.Match> matches = matching.findMatches(BloodGroup.AB_POS, "Thane");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(matches).isNotEmpty();
        assertThat(elapsedMillis).as("shortlist for 500 donors took %d ms", elapsedMillis).isLessThan(2_000);
    }

    @Test
    @DisplayName("the clock is injected, so eligibility is evaluated against the supplied date and not the wall clock")
    void clockIsInjected() {
        register("Donated 90 days before the fixed date", BloodGroup.O_NEG, "Thane", 90L, true);

        MatchingService withLaterClock = new MatchingService(donors,
                Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThat(matching.findMatches(BloodGroup.O_NEG, "Thane")).hasSize(1);
        assertThat(withLaterClock.findMatches(BloodGroup.O_NEG, "Thane")).hasSize(1);
    }
}
