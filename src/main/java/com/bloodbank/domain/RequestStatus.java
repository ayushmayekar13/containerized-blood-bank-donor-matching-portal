package com.bloodbank.domain;

import java.util.List;
import java.util.Set;

/**
 * The lifecycle of a blood request, and the only transitions that are legal
 * (requirements FR-17 and FR-18).
 *
 * <pre>
 *            Staff or Admin            Admin
 *   NEW ─────────────────────► MATCHED ──────► FULFILLED   (terminal)
 *    │                            │
 *    └──────────── Admin ─────────┴──────────► REJECTED    (terminal)
 * </pre>
 *
 * <p>The state machine lives on the enum so that it can be asserted without a
 * database, a web request or a security context. <em>Which role</em> may perform
 * each transition is enforced separately, in {@code RequestService}, because that
 * is an authorisation concern rather than a property of the state itself.
 *
 * <p>Both {@code FULFILLED} and {@code REJECTED} are terminal: a request that has
 * been closed is never reopened, it is superseded by a new request. That keeps the
 * audit trail of what actually happened intact.
 */
public enum RequestStatus {

    /** Recorded, not yet matched to a donor. */
    NEW("New"),

    /** A compatible, eligible, available donor has been assigned. */
    MATCHED("Matched"),

    /** The donation took place; the matched donor's donation date has been stamped. */
    FULFILLED("Fulfilled"),

    /** Closed without a donation, with a recorded reason. */
    REJECTED("Rejected");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** The states this state may legally move to. Empty for terminal states. */
    public List<RequestStatus> allowedNext() {
        return switch (this) {
            case NEW       -> List.of(MATCHED, REJECTED);
            case MATCHED   -> List.of(FULFILLED, REJECTED);
            case FULFILLED, REJECTED -> List.of();
        };
    }

    /** Whether no further transition is possible. */
    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /** Whether a move to {@code target} is one of the legal transitions. */
    public boolean canMoveTo(RequestStatus target) {
        return target != null && allowedNext().contains(target);
    }

    /** Whether the request is still being worked on — used by dashboard counts. */
    public boolean isOpen() {
        return this == NEW || this == MATCHED;
    }

    /** The open states, as a set, for repository queries. */
    public static Set<RequestStatus> openStates() {
        return Set.of(NEW, MATCHED);
    }

    public static RequestStatus fromText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        for (RequestStatus status : values()) {
            if (status.name().equalsIgnoreCase(text.trim()) || status.label.equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown request status: " + text);
    }
}
