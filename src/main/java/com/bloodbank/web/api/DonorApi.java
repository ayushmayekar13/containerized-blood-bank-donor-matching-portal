package com.bloodbank.web.api;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import com.bloodbank.service.DonorService;
import com.bloodbank.service.MatchingService;
import com.bloodbank.web.DonorForm;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * JSON API for the donor registry (requirement FR-25).
 *
 * <p>It calls the same services as the HTML controllers, which is the point: a
 * rule enforced for the browser is automatically enforced for the API, and the
 * Selenium journeys and the Ansible health check can assert against the same
 * behaviour.
 */
@RestController
@RequestMapping("/api/donors")
public class DonorApi {

    /** Response representation; derived values are computed here, not stored. */
    public record DonorView(Long id, String fullName, String bloodGroup, String phone, String email,
                            String city, LocalDate lastDonationDate, boolean available,
                            boolean eligible, LocalDate nextEligibleDate) {

        static DonorView of(Donor donor, LocalDate today) {
            return new DonorView(donor.getId(), donor.getFullName(), donor.getBloodGroup().getLabel(),
                                 donor.getPhone(), donor.getEmail(), donor.getCity(),
                                 donor.getLastDonationDate(), donor.isAvailable(),
                                 donor.isEligible(today), donor.nextEligibleDate(today));
        }
    }

    private final DonorService donors;
    private final MatchingService matching;

    public DonorApi(DonorService donors, MatchingService matching) {
        this.donors = donors;
        this.matching = matching;
    }

    @GetMapping
    public List<DonorView> list(@RequestParam(required = false) String group,
                               @RequestParam(required = false) String city,
                               @RequestParam(defaultValue = "false") boolean availableOnly) {
        LocalDate today = donors.today();
        return donors.search(group, city, availableOnly).stream()
                     .map(donor -> DonorView.of(donor, today))
                     .toList();
    }

    @GetMapping("/{id}")
    public DonorView byId(@PathVariable Long id) {
        return DonorView.of(donors.findById(id), donors.today());
    }

    @PostMapping
    public ResponseEntity<DonorView> create(@Valid @RequestBody DonorForm form) {
        Donor saved = donors.create(form.toDonor());
        return ResponseEntity.created(URI.create("/api/donors/" + saved.getId()))
                             .body(DonorView.of(saved, donors.today()));
    }

    @PutMapping("/{id}")
    public DonorView update(@PathVariable Long id, @Valid @RequestBody DonorForm form) {
        return DonorView.of(donors.update(id, form.toDonor()), donors.today());
    }

    @PatchMapping("/{id}/availability")
    public DonorView setAvailability(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean available = Boolean.TRUE.equals(body.get("available"));
        return DonorView.of(donors.setAvailability(id, available), donors.today());
    }

    /**
     * The donors who could actually be called for a recipient group: compatible,
     * eligible and available, already ranked (FR-13 to FR-16). This is the
     * endpoint the performance criterion S1 is measured against.
     */
    @GetMapping("/eligible")
    public List<MatchView> eligible(@RequestParam String group,
                                    @RequestParam(required = false) String city) {
        BloodGroup recipient = BloodGroup.fromText(group);
        return matching.findMatches(recipient, (city == null || city.isBlank()) ? null : city)
                       .stream().map(MatchView::of).toList();
    }

    /** Ranked match representation, with the reasons for the rank made explicit. */
    public record MatchView(Long donorId, String fullName, String bloodGroup, String city,
                            boolean exactGroupMatch, boolean sameCity,
                            Long daysSinceLastDonation, int rank) {

        static MatchView of(MatchingService.Match match) {
            return new MatchView(match.donorId(), match.fullName(), match.bloodGroupLabel(),
                                 match.city(), match.exactGroupMatch(), match.sameCity(),
                                 match.neverDonated() ? null : match.daysSinceLastDonation(),
                                 match.rank());
        }
    }
}
