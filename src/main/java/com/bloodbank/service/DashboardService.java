package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.RequestStatus;
import com.bloodbank.repo.BloodRequestRepository;
import com.bloodbank.repo.DonorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supply against demand, per blood group (requirements FR-22 and FR-23).
 *
 * <p>Two numbers per group, and the difference between them is the whole point:
 * <ul>
 *   <li><strong>registered</strong> — everyone on the register for that group;</li>
 *   <li><strong>callable</strong> — those who are available <em>and</em> past the
 *       90-day interval, that is, people who could actually be phoned today.</li>
 * </ul>
 *
 * <p>The callable figure is obtained from {@link MatchingService}, not computed
 * here. Defect BUG-02 was exactly this: the dashboard had its own supply
 * calculation that counted unavailable donors as stock, so the dashboard
 * disagreed with the shortlist the technician was looking at. One code path makes
 * that disagreement impossible.
 *
 * <p>A <strong>shortage</strong> is a group with at least one open request and zero
 * callable donors — the condition that, in the manual process, is discovered only
 * when a request fails.
 */
@Service
public class DashboardService {

    /** Everything the dashboard screen and {@code GET /api/dashboard} need. */
    public record Snapshot(Map<String, Long> donorsByGroup,
                           Map<String, Long> callableByGroup,
                           Map<String, Long> requestsByStatus,
                           List<String> shortages,
                           long totalDonors,
                           long callableDonors,
                           long openRequests) {

        public boolean hasShortages() {
            return !shortages.isEmpty();
        }
    }

    private final DonorRepository donors;
    private final BloodRequestRepository requests;
    private final MatchingService matching;

    public DashboardService(DonorRepository donors, BloodRequestRepository requests,
                            MatchingService matching) {
        this.donors = donors;
        this.requests = requests;
        this.matching = matching;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        Map<String, Long> registered = new LinkedHashMap<>();
        Map<String, Long> callable = new LinkedHashMap<>();

        for (BloodGroup group : BloodGroup.values()) {
            registered.put(group.getLabel(), donors.countByBloodGroup(group));

            // Same predicate as the shortlist, by construction - see BUG-02.
            callable.put(group.getLabel(), matching.countMatches(group));
        }

        // The per-group figures deliberately overlap: one O- donor is callable for
        // all eight recipient groups, so summing the map would count that donor
        // eight times. The distinct number of people who could be phoned today is
        // exactly the shortlist for AB+, because AB+ is the universal recipient
        // and therefore accepts every group.
        long callableTotal = matching.countMatches(BloodGroup.AB_POS);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) {
            byStatus.put(status.name(), requests.countByStatus(status));
        }

        List<BloodGroup> groupsWithOpenRequests =
                requests.findGroupsWithRequestsInStatus(RequestStatus.openStates());

        // FR-23: demand with no supply. Note this asks whether anyone could be
        // called FOR that group, which is wider than "donors of that group".
        List<String> shortages = groupsWithOpenRequests.stream()
                .filter(group -> matching.countMatches(group) == 0)
                .map(BloodGroup::getLabel)
                .sorted()
                .toList();

        long openRequests = byStatus.getOrDefault(RequestStatus.NEW.name(), 0L)
                          + byStatus.getOrDefault(RequestStatus.MATCHED.name(), 0L);

        return new Snapshot(registered, callable, byStatus, shortages,
                            donors.count(), callableTotal, openRequests);
    }
}
