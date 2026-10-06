package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.Donor;
import com.bloodbank.domain.RequestStatus;
import com.bloodbank.repo.BloodRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Blood request lifecycle, including the role-based status workflow
 * (requirements FR-10 to FR-21).
 *
 * <p>Every transition passes through this class, and every transition is checked
 * twice:
 * <ol>
 *   <li><strong>Is the transition legal?</strong> Decided by
 *       {@link RequestStatus#canMoveTo} — a question about the state machine.
 *       Violations are HTTP <strong>409</strong>.</li>
 *   <li><strong>Is this role permitted to perform it?</strong> Decided here — a
 *       question about authorisation. Violations are HTTP <strong>403</strong>.</li>
 * </ol>
 *
 * <p>Keeping the two separate matters: Staff attempting {@code NEW → FULFILLED} is
 * <em>both</em> an illegal transition and an insufficient role, and the examiner's
 * natural question — "which error does the user see?" — has a defined answer. The
 * role is checked first, because telling a user that a transition is impossible
 * when they were never allowed to attempt it is misleading.
 *
 * <p>The permission table (FR-17):
 * <pre>
 *   NEW     → MATCHED     STAFF or ADMIN
 *   NEW     → REJECTED    ADMIN only
 *   MATCHED → FULFILLED   ADMIN only
 *   MATCHED → REJECTED    ADMIN only
 * </pre>
 */
@Service
public class RequestService {

    /** The transition is not permitted by the state machine. Surfaced as HTTP 409. */
    public static class IllegalTransitionException extends RuntimeException {
        public IllegalTransitionException(String message) { super(message); }
    }

    /** The actor's role may not perform this transition. Surfaced as HTTP 403. */
    public static class InsufficientRoleException extends RuntimeException {
        public InsufficientRoleException(String message) { super(message); }
    }

    /** The chosen donor is incompatible, ineligible or unavailable. Surfaced as HTTP 422. */
    public static class InvalidMatchException extends RuntimeException {
        public InvalidMatchException(String message) { super(message); }
    }

    /** Unknown request id. Surfaced as HTTP 404. */
    public static class RequestNotFoundException extends RuntimeException {
        public RequestNotFoundException(Long id) { super("No blood request with id " + id); }
    }

    private final BloodRequestRepository requests;
    private final DonorService donors;
    private final MatchingService matching;
    private final Clock clock;

    public RequestService(BloodRequestRepository requests, DonorService donors,
                          MatchingService matching, Clock clock) {
        this.requests = requests;
        this.donors = donors;
        this.matching = matching;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<BloodRequest> findAll() {
        return search(null, null);
    }

    /**
     * Most urgent first, then oldest first - the order a transfusion desk works
     * through a queue.
     *
     * <p>Applied in Java rather than in the query because the urgency column is
     * stored as text, so SQL would sort it alphabetically (CRITICAL, HIGH, LOW,
     * MEDIUM) instead of by severity. See BUG-05 and the note on
     * {@code BloodRequestRepository.search}.
     */
    private static final java.util.Comparator<BloodRequest> WORKLIST_ORDER =
            java.util.Comparator.comparingInt((BloodRequest r) -> r.getUrgency().getWeight())
                    .reversed()
                    .thenComparing(BloodRequest::getCreatedAt,
                                   java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                    .thenComparing(BloodRequest::getId,
                                   java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));

    @Transactional(readOnly = true)
    public BloodRequest findById(Long id) {
        return requests.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
    }

    /** FR-12: search by status and blood group, either filter optional, combined with AND. */
    @Transactional(readOnly = true)
    public List<BloodRequest> search(String status, String group) {
        RequestStatus statusFilter = (status == null || status.isBlank())
                ? null : RequestStatus.fromText(status);
        BloodGroup groupFilter = (group == null || group.isBlank())
                ? null : BloodGroup.fromText(group);
        return requests.search(statusFilter, groupFilter).stream()
                       .sorted(WORKLIST_ORDER)
                       .toList();
    }

    /** The ranked shortlist for a request — the same engine used by the match preview screen. */
    @Transactional(readOnly = true)
    public List<MatchingService.Match> matchesFor(Long requestId) {
        BloodRequest request = findById(requestId);
        return matching.findMatches(request.getBloodGroup(), request.getCity());
    }

    @Transactional(readOnly = true)
    public long countByStatus(RequestStatus status) {
        return requests.countByStatus(status);
    }

    // ------------------------------------------------------------------ writing

    /** FR-11: a new request starts in {@code NEW} with its creator recorded. */
    @Transactional
    public BloodRequest create(BloodRequest request, Actor actor) {
        requireStaffOrAdmin(actor, "record a blood request");
        request.recordCreation(actor.username(), Instant.now(clock));
        return requests.save(request);
    }

    /**
     * FR-19: {@code NEW → MATCHED}, assigning a donor who must genuinely be a
     * valid match. Permitted to STAFF and ADMIN.
     */
    @Transactional
    public BloodRequest assignDonor(Long requestId, Long donorId, Actor actor) {
        requireStaffOrAdmin(actor, "assign a matched donor");

        BloodRequest request = findById(requestId);
        requireLegalTransition(request, RequestStatus.MATCHED);

        Donor donor = donors.findById(donorId);
        if (!matching.isValidMatch(request.getBloodGroup(), donor)) {
            // 422 rather than 400: the input is well-formed but the donor cannot
            // give blood for this patient, which is a business rule, not a typo.
            throw new InvalidMatchException(
                    "%s (%s) is not a valid match for a %s request: either incompatible, unavailable, or inside the %d-day interval"
                            .formatted(donor.getFullName(), donor.getBloodGroup().getLabel(),
                                       request.getBloodGroup().getLabel(), Donor.DONATION_INTERVAL_DAYS));
        }

        request.assignDonor(donor, actor.username(), Instant.now(clock));
        return request;
    }

    /**
     * FR-20: {@code MATCHED → FULFILLED}. <strong>ADMIN only.</strong>
     *
     * <p>Stamps the matched donor's last-donation date in the same transaction, so
     * a donation can never be recorded without the request advancing, nor the
     * request advance without the donor becoming ineligible for 90 days.
     */
    @Transactional
    public BloodRequest fulfil(Long requestId, Actor actor) {
        // DELIBERATE DEFECT - BUG-06, introduced for Task 10.
        //
        // The ADMIN-only guard below has been weakened to allow STAFF as well,
        // which is exactly the regression AC-07.4 forbids: any technician could
        // close any request, and the accountability the workflow exists to
        // provide would be gone.
        //
        // It is a realistic mistake rather than a contrived one - it looks like
        // someone "fixing" a support complaint that staff could not complete
        // their own requests. The 178 unit tests do not all pass with this in
        // place, but the point being demonstrated is that the SELENIUM gate
        // stops the deployment: the pipeline must go red and the Promote stage
        // must never be entered.
        requireStaffOrAdmin(actor, "confirm that a donation took place");

        BloodRequest request = findById(requestId);
        requireLegalTransition(request, RequestStatus.FULFILLED);

        request.markFulfilled(actor.username(), Instant.now(clock));
        donors.recordDonation(request.getMatchedDonor().getId());
        return request;
    }

    /** FR-21: {@code * → REJECTED} with a mandatory reason. <strong>ADMIN only.</strong> */
    @Transactional
    public BloodRequest reject(Long requestId, String reason, Actor actor) {
        requireAdmin(actor, "reject a blood request");

        BloodRequest request = findById(requestId);
        requireLegalTransition(request, RequestStatus.REJECTED);

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a rejection reason is required");
        }
        request.markRejected(reason, actor.username(), Instant.now(clock));
        return request;
    }

    // ------------------------------------------------------------------ guards

    private void requireStaffOrAdmin(Actor actor, String what) {
        if (!actor.isStaffOrAdmin()) {
            throw new InsufficientRoleException(
                    "%s is not permitted to %s (requires STAFF or ADMIN)".formatted(actor.username(), what));
        }
    }

    private void requireAdmin(Actor actor, String what) {
        if (!actor.isAdmin()) {
            throw new InsufficientRoleException(
                    "%s is not permitted to %s (requires ADMIN)".formatted(actor.username(), what));
        }
    }

    private void requireLegalTransition(BloodRequest request, RequestStatus target) {
        if (!request.getStatus().canMoveTo(target)) {
            String detail = request.getStatus().isTerminal()
                    ? "%s is a terminal state; a closed request is never reopened".formatted(request.getStatus())
                    : "legal next states are %s".formatted(request.getStatus().allowedNext());
            throw new IllegalTransitionException(
                    "Request %d cannot move from %s to %s - %s"
                            .formatted(request.getId(), request.getStatus(), target, detail));
        }
    }
}
