package com.bloodbank.web.api;

import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.RequestStatus;
import com.bloodbank.service.RequestService;
import com.bloodbank.web.RequestForm;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JSON API for blood requests, including the three workflow transitions
 * (requirement FR-25).
 *
 * <p>It calls the same {@code RequestService} methods as the HTML controller, so
 * the role and transition guards apply identically. That is what the viva
 * demonstration relies on: {@code curl} as {@code staff} against
 * {@code /fulfil} returns <strong>403</strong>, which proves the restriction is
 * server-side and not merely a hidden button.
 */
@RestController
@RequestMapping("/api/requests")
public class RequestApi {

    public record DonorRef(Long id, String fullName, String bloodGroup) { }

    public record RequestView(Long id, String patientName, String bloodGroup, int unitsNeeded,
                              String hospital, String city, String urgency, String status,
                              DonorRef matchedDonor, String rejectionReason,
                              List<String> allowedNext, String createdBy, Instant createdAt,
                              String lastModifiedBy, Instant lastModifiedAt) {

        static RequestView of(BloodRequest request) {
            DonorRef donor = request.getMatchedDonor() == null ? null
                    : new DonorRef(request.getMatchedDonor().getId(),
                                   request.getMatchedDonor().getFullName(),
                                   request.getMatchedDonor().getBloodGroup().getLabel());
            return new RequestView(request.getId(), request.getPatientName(),
                    request.getBloodGroup().getLabel(), request.getUnitsNeeded(),
                    request.getHospital(), request.getCity(), request.getUrgency().name(),
                    request.getStatus().name(), donor, request.getRejectionReason(),
                    request.allowedNext().stream().map(RequestStatus::name).toList(),
                    request.getCreatedBy(), request.getCreatedAt(),
                    request.getLastModifiedBy(), request.getLastModifiedAt());
        }
    }

    private final RequestService requests;

    public RequestApi(RequestService requests) {
        this.requests = requests;
    }

    @GetMapping
    public List<RequestView> list(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String group) {
        return requests.search(status, group).stream().map(RequestView::of).toList();
    }

    @GetMapping("/{id}")
    public RequestView byId(@PathVariable Long id) {
        return RequestView.of(requests.findById(id));
    }

    @PostMapping
    public ResponseEntity<RequestView> create(@Valid @RequestBody RequestForm form,
                                              Authentication authentication) {
        BloodRequest saved = requests.create(form.toRequest(), ActorAdapter.from(authentication));
        return ResponseEntity.created(URI.create("/api/requests/" + saved.getId()))
                             .body(RequestView.of(saved));
    }

    /** The ranked shortlist for this request — the endpoint criterion S1 is measured against. */
    @GetMapping("/{id}/matches")
    public List<DonorApi.MatchView> matches(@PathVariable Long id) {
        return requests.matchesFor(id).stream().map(DonorApi.MatchView::of).toList();
    }

    @PostMapping("/{id}/match")
    public RequestView match(@PathVariable Long id, @RequestBody Map<String, Long> body,
                             Authentication authentication) {
        Long donorId = body.get("donorId");
        if (donorId == null) {
            throw new IllegalArgumentException("donorId is required");
        }
        return RequestView.of(requests.assignDonor(id, donorId, ActorAdapter.from(authentication)));
    }

    /** ADMIN only — a STAFF caller receives 403 from the service layer. */
    @PostMapping("/{id}/fulfil")
    public RequestView fulfil(@PathVariable Long id, Authentication authentication) {
        return RequestView.of(requests.fulfil(id, ActorAdapter.from(authentication)));
    }

    /** ADMIN only, and a reason is mandatory. */
    @PostMapping("/{id}/reject")
    public RequestView reject(@PathVariable Long id, @RequestBody Map<String, String> body,
                              Authentication authentication) {
        return RequestView.of(requests.reject(id, body.get("reason"), ActorAdapter.from(authentication)));
    }
}
