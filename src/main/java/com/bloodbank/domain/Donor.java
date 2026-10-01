package com.bloodbank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A registered blood donor.
 *
 * <p>Eligibility is deliberately <strong>not</strong> a stored column. Storing it
 * would require a nightly job and would be wrong the moment the clock passed
 * midnight, so it is computed on read from {@link #lastDonationDate} against the
 * {@value #DONATION_INTERVAL_DAYS}-day whole-blood interval (requirement FR-08).
 */
@Entity
@Table(name = "donor")
public class Donor {

    /**
     * Minimum interval between two whole-blood donations. Ninety days (roughly
     * three months) is the interval the portal enforces; it is the only medical
     * deferral rule modelled in the MVP, which is recorded as a known limitation
     * in {@code docs/SRS.md} section 5.
     */
    public static final int DONATION_INTERVAL_DAYS = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 80, message = "Full name must be between 3 and 80 characters")
    @Column(nullable = false, length = 80)
    private String fullName;

    @NotNull(message = "Blood group is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BloodGroup bloodGroup;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\d{10}", message = "Phone number must be exactly 10 digits")
    @Column(nullable = false, length = 10, unique = true)
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Column(nullable = false, length = 120)
    private String email;

    @NotBlank(message = "City is required")
    @Size(min = 2, max = 60, message = "City must be between 2 and 60 characters")
    @Column(nullable = false, length = 60)
    private String city;

    /** {@code null} means the donor has never donated, which counts as eligible. */
    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Column(nullable = false)
    private boolean available = true;

    @Size(max = 255, message = "Notes must be at most 255 characters")
    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Donor() {
        // required by JPA
    }

    public Donor(String fullName, BloodGroup bloodGroup, String phone, String email, String city) {
        this.fullName = fullName;
        this.bloodGroup = bloodGroup;
        this.phone = phone;
        this.email = email;
        this.city = city;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------- behaviour

    /**
     * Whether enough time has passed since the last donation for this donor to
     * donate again (requirement FR-08). A donor who has never donated is eligible.
     *
     * <p>The comparison is {@code >=}: a donor whose last donation was exactly
     * {@value #DONATION_INTERVAL_DAYS} days ago <em>is</em> eligible. The boundary
     * is tested at 89, 90 and 91 days, because getting it wrong by one day was
     * defect BUG-01.
     */
    public boolean isEligible(LocalDate today) {
        if (lastDonationDate == null) {
            return true;
        }
        return daysSinceLastDonation(today) >= DONATION_INTERVAL_DAYS;
    }

    /** Days since the last donation, or {@link Long#MAX_VALUE} if never donated. */
    public long daysSinceLastDonation(LocalDate today) {
        if (lastDonationDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(lastDonationDate, today);
    }

    /** The first date on which this donor may donate again. */
    public LocalDate nextEligibleDate(LocalDate today) {
        if (lastDonationDate == null) {
            return today;
        }
        LocalDate next = lastDonationDate.plusDays(DONATION_INTERVAL_DAYS);
        return next.isBefore(today) ? today : next;
    }

    /**
     * Whether this donor can actually be called for a request: available and past
     * the donation interval. Compatibility is a property of the blood groups and is
     * applied separately by {@code MatchingService}.
     */
    public boolean isCallable(LocalDate today) {
        return available && isEligible(today);
    }

    /** Records a completed donation, which starts a new ineligibility window. */
    public void recordDonation(LocalDate donatedOn) {
        this.lastDonationDate = donatedOn;
    }

    // ---------------------------------------------------------------- accessors

    public Long getId() { return id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public LocalDate getLastDonationDate() { return lastDonationDate; }
    public void setLastDonationDate(LocalDate lastDonationDate) { this.lastDonationDate = lastDonationDate; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Donor{id=%s, name='%s', group=%s, city='%s', available=%s}"
                .formatted(id, fullName, bloodGroup == null ? null : bloodGroup.getLabel(), city, available);
    }
}
