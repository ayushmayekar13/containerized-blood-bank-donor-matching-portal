package com.bloodbank.service;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import com.bloodbank.repo.DonorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Donor registry operations: create, view, update and search (requirements
 * FR-01 to FR-09).
 *
 * <p>All validation that is not a simple field constraint lives here rather than
 * in the controller, so the REST API cannot bypass a rule that the HTML form
 * enforces.
 */
@Service
public class DonorService {

    /** Raised when a phone number is already registered (surfaced as HTTP 409). */
    public static class DuplicatePhoneException extends RuntimeException {
        public DuplicatePhoneException(String phone) {
            super("A donor with phone number " + phone + " is already registered");
        }
    }

    /** Raised when an id does not exist (surfaced as HTTP 404). */
    public static class DonorNotFoundException extends RuntimeException {
        public DonorNotFoundException(Long id) {
            super("No donor with id " + id);
        }
    }

    private final DonorRepository donors;
    private final Clock clock;

    public DonorService(DonorRepository donors, Clock clock) {
        this.donors = donors;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Donor> findAll() {
        return donors.findAll(org.springframework.data.domain.Sort.by("fullName"));
    }

    @Transactional(readOnly = true)
    public Donor findById(Long id) {
        return donors.findById(id).orElseThrow(() -> new DonorNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<Donor> findByPhone(String phone) {
        return donors.findByPhone(phone);
    }

    /**
     * Searches the registry (FR-04). Every filter is optional and the supplied
     * ones combine with logical AND, so one method serves the whole search screen.
     *
     * @param group         blood group label or enum name, blank for any
     * @param city          partial, case-insensitive city match, blank for any
     * @param availableOnly exclude donors flagged unavailable
     */
    @Transactional(readOnly = true)
    public List<Donor> search(String group, String city, boolean availableOnly) {
        BloodGroup bloodGroup = (group == null || group.isBlank()) ? null : BloodGroup.fromText(group);
        String cityFilter = (city == null || city.isBlank()) ? null : city.trim();
        return donors.search(bloodGroup, cityFilter, availableOnly);
    }

    @Transactional
    public Donor create(Donor donor) {
        if (donors.existsByPhone(donor.getPhone())) {
            throw new DuplicatePhoneException(donor.getPhone());
        }
        return donors.save(donor);
    }

    @Transactional
    public Donor update(Long id, Donor changes) {
        Donor existing = findById(id);
        donors.findByPhone(changes.getPhone())
              .filter(other -> !other.getId().equals(id))
              .ifPresent(other -> { throw new DuplicatePhoneException(changes.getPhone()); });

        existing.setFullName(changes.getFullName());
        existing.setBloodGroup(changes.getBloodGroup());
        existing.setPhone(changes.getPhone());
        existing.setEmail(changes.getEmail());
        existing.setCity(changes.getCity());
        existing.setLastDonationDate(changes.getLastDonationDate());
        existing.setAvailable(changes.isAvailable());
        existing.setNotes(changes.getNotes());
        return existing;
    }

    @Transactional
    public Donor setAvailability(Long id, boolean available) {
        Donor donor = findById(id);
        donor.setAvailable(available);
        return donor;
    }

    /**
     * Records a completed donation, which starts the donor's 90-day
     * ineligibility window (FR-20). Called by the request workflow when a
     * request is fulfilled.
     */
    @Transactional
    public Donor recordDonation(Long id) {
        Donor donor = findById(id);
        donor.recordDonation(LocalDate.now(clock));
        return donor;
    }

    /** Today's date according to the injected clock — used by views to render eligibility. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public long count() {
        return donors.count();
    }
}
