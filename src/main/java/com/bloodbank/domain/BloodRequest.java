package com.bloodbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * A recorded need for blood: which patient, which group, how many units, where,
 * and how urgently (requirements FR-10 to FR-21).
 *
 * <p>The entity keeps the audit fields that make the workflow accountable —
 * who created the request and who last changed it, with timestamps (NFR-15) —
 * and it exposes the status transitions as methods rather than letting callers
 * assign {@code status} directly. Legality is checked by
 * {@link RequestStatus#canMoveTo}; the role check sits in {@code RequestService},
 * because it is an authorisation concern.
 */
@Entity
@Table(name = "blood_request")
public class BloodRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Patient name is required")
    @Size(min = 3, max = 80, message = "Patient name must be between 3 and 80 characters")
    @Column(nullable = false, length = 80)
    private String patientName;

    @NotNull(message = "Blood group is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BloodGroup bloodGroup;

    @Min(value = 1, message = "At least 1 unit is required")
    @Max(value = 10, message = "A single request may ask for at most 10 units")
    @Column(nullable = false)
    private int unitsNeeded;

    @NotBlank(message = "Hospital is required")
    @Size(min = 3, max = 100, message = "Hospital must be between 3 and 100 characters")
    @Column(nullable = false, length = 100)
    private String hospital;

    @NotBlank(message = "City is required")
    @Size(min = 2, max = 60, message = "City must be between 2 and 60 characters")
    @Column(nullable = false, length = 60)
    private String city;

    @NotNull(message = "Urgency is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RequestStatus status = RequestStatus.NEW;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "matched_donor_id")
    private Donor matchedDonor;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "created_by", length = 60, updatable = false)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "last_modified_by", length = 60)
    private String lastModifiedBy;

    @Column(name = "last_modified_at")
    private Instant lastModifiedAt;

    protected BloodRequest() {
        // required by JPA
    }

    public BloodRequest(String patientName, BloodGroup bloodGroup, int unitsNeeded,
                        String hospital, String city, Urgency urgency) {
        this.patientName = patientName;
        this.bloodGroup = bloodGroup;
        this.unitsNeeded = unitsNeeded;
        this.hospital = hospital;
        this.city = city;
        this.urgency = urgency;
        this.status = RequestStatus.NEW;
    }

    // ---------------------------------------------------------------- behaviour

    /** Stamps creation audit fields. Called by the service, not by JPA, so the acting user is known. */
    public void recordCreation(String username, Instant when) {
        this.createdBy = username;
        this.createdAt = when;
        this.lastModifiedBy = username;
        this.lastModifiedAt = when;
    }

    /**
     * FR-19: assigns the matched donor and moves to {@code MATCHED}.
     *
     * @throws IllegalStateException when the current status does not permit it
     */
    public void assignDonor(Donor donor, String username, Instant when) {
        requireTransitionTo(RequestStatus.MATCHED);
        if (donor == null) {
            throw new IllegalArgumentException("a matched donor is required");
        }
        this.matchedDonor = donor;
        this.status = RequestStatus.MATCHED;
        touch(username, when);
    }

    /** FR-20: the donation took place. The donor's donation date is stamped by the service. */
    public void markFulfilled(String username, Instant when) {
        requireTransitionTo(RequestStatus.FULFILLED);
        this.status = RequestStatus.FULFILLED;
        touch(username, when);
    }

    /** FR-21: closes the request without a donation. A reason is mandatory. */
    public void markRejected(String reason, String username, Instant when) {
        requireTransitionTo(RequestStatus.REJECTED);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a rejection reason is required");
        }
        this.rejectionReason = reason.trim();
        this.status = RequestStatus.REJECTED;
        touch(username, when);
    }

    private void requireTransitionTo(RequestStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException(
                    "Request %s cannot move from %s to %s".formatted(id, status, target));
        }
    }

    private void touch(String username, Instant when) {
        this.lastModifiedBy = username;
        this.lastModifiedAt = when;
    }

    /** Drives which action buttons the UI offers, and the {@code allowedNext} field in the API. */
    public List<RequestStatus> allowedNext() {
        return status.allowedNext();
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    // ---------------------------------------------------------------- accessors

    public Long getId() { return id; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }

    public int getUnitsNeeded() { return unitsNeeded; }
    public void setUnitsNeeded(int unitsNeeded) { this.unitsNeeded = unitsNeeded; }

    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }

    public RequestStatus getStatus() { return status; }

    public Donor getMatchedDonor() { return matchedDonor; }

    public String getRejectionReason() { return rejectionReason; }

    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getLastModifiedBy() { return lastModifiedBy; }
    public Instant getLastModifiedAt() { return lastModifiedAt; }

    @Override
    public String toString() {
        return "BloodRequest{id=%s, patient='%s', group=%s, units=%d, status=%s}"
                .formatted(id, patientName, bloodGroup == null ? null : bloodGroup.getLabel(),
                           unitsNeeded, status);
    }
}
