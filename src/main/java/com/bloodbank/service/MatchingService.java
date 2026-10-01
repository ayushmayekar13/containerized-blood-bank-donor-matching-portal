package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import com.bloodbank.repo.DonorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The donor matching engine — the core logic of the portal (requirements FR-13
 * to FR-16).
 *
 * <p>A donor <em>matches</em> a need when all three independent predicates hold:
 * <ol>
 *   <li><strong>compatible</strong> — the recipient group can receive the donor's
 *       red cells ({@link BloodGroup#canReceiveFrom});</li>
 *   <li><strong>available</strong> — the donor's availability flag is set;</li>
 *   <li><strong>eligible</strong> — at least 90 days since the last donation
 *       ({@link Donor#isEligible}).</li>
 * </ol>
 *
 * <p>Matches are then ranked, and the ranking carries real decisions:
 * <ol>
 *   <li><em>Exact group match first.</em> A universal O- donor should not be spent
 *       on an O+ patient while an O+ donor is available, because O- is the scarcest
 *       and most widely needed stock.</li>
 *   <li><em>Same city next.</em> Travel time is the dominant part of time-to-match.</li>
 *   <li><em>Longest since last donation next.</em> This rotates the load across the
 *       register instead of repeatedly calling the same willing people.</li>
 *   <li><em>Name last.</em> Purely so that the ordering is deterministic and the
 *       unit tests cannot be flaky.</li>
 * </ol>
 *
 * <p>The {@link Clock} is injected rather than calling {@code LocalDate.now()}
 * directly, so that eligibility boundaries can be tested at exactly 89, 90 and 91
 * days without waiting for real time to pass.
 */
@Service
public class MatchingService {

    private final DonorRepository donors;
    private final Clock clock;

    public MatchingService(DonorRepository donors, Clock clock) {
        this.donors = donors;
        this.clock = clock;
    }

    /** A donor paired with the reasons it was ranked where it was. */
    public record Match(Donor donor,
                        boolean exactGroupMatch,
                        boolean sameCity,
                        long daysSinceLastDonation,
                        int rank) {

        public Long donorId() { return donor.getId(); }
        public String fullName() { return donor.getFullName(); }
        public String bloodGroupLabel() { return donor.getBloodGroup().getLabel(); }
        public String city() { return donor.getCity(); }

        /** {@code Long.MAX_VALUE} means the donor has never donated. */
        public boolean neverDonated() { return daysSinceLastDonation == Long.MAX_VALUE; }
    }

    /**
     * Ranked matches for a recipient group, preferring donors in {@code city}.
     *
     * @param recipientGroup the patient's blood group, required
     * @param city           the request's city; {@code null} disables the proximity rule
     * @return matches best first; empty when nobody can be called
     */
    @Transactional(readOnly = true)
    public List<Match> findMatches(BloodGroup recipientGroup, String city) {
        if (recipientGroup == null) {
            throw new IllegalArgumentException("recipient blood group must not be null");
        }
        LocalDate today = LocalDate.now(clock);

        List<Donor> candidates = donors.findByBloodGroupIn(recipientGroup.compatibleDonorGroups());

        List<Donor> callable = candidates.stream()
                .filter(donor -> donor.isCallable(today))
                .sorted(ranking(recipientGroup, city, today))
                .toList();

        return java.util.stream.IntStream.range(0, callable.size())
                .mapToObj(index -> {
                    Donor donor = callable.get(index);
                    return new Match(
                            donor,
                            donor.getBloodGroup() == recipientGroup,
                            city != null && city.equalsIgnoreCase(donor.getCity()),
                            donor.daysSinceLastDonation(today),
                            index + 1);
                })
                .toList();
    }

    /** Convenience overload used by the dashboard, where proximity is irrelevant. */
    @Transactional(readOnly = true)
    public List<Match> findMatches(BloodGroup recipientGroup) {
        return findMatches(recipientGroup, null);
    }

    /**
     * How many donors could be called right now for a recipient group. Used for the
     * dashboard's supply figures and shortage flags (FR-22, FR-23), and deliberately
     * the same predicate as {@link #findMatches} so the dashboard can never disagree
     * with the shortlist — the cause of defect BUG-02.
     */
    @Transactional(readOnly = true)
    public long countMatches(BloodGroup recipientGroup) {
        return findMatches(recipientGroup, null).size();
    }

    /** Whether a specific donor is a valid match for a recipient group (used to validate a transition). */
    @Transactional(readOnly = true)
    public boolean isValidMatch(BloodGroup recipientGroup, Donor donor) {
        return recipientGroup.canReceiveFrom(donor.getBloodGroup())
            && donor.isCallable(LocalDate.now(clock));
    }

    private Comparator<Donor> ranking(BloodGroup recipientGroup, String city, LocalDate today) {
        Comparator<Donor> exactGroupFirst =
                Comparator.comparing((Donor d) -> d.getBloodGroup() == recipientGroup).reversed();
        Comparator<Donor> sameCityFirst =
                Comparator.comparing((Donor d) -> city != null && city.equalsIgnoreCase(d.getCity())).reversed();
        Comparator<Donor> longestSinceDonationFirst =
                Comparator.comparingLong((Donor d) -> d.daysSinceLastDonation(today)).reversed();
        Comparator<Donor> byName =
                Comparator.comparing(Donor::getFullName, String.CASE_INSENSITIVE_ORDER);

        return exactGroupFirst
                .thenComparing(sameCityFirst)
                .thenComparing(longestSinceDonationFirst)
                .thenComparing(byName);
    }
}
