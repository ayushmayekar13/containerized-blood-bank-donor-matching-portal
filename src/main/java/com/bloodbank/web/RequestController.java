package com.bloodbank.web;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.BloodRequest;
import com.bloodbank.domain.RequestStatus;
import com.bloodbank.domain.Urgency;
import com.bloodbank.service.DonorService;
import com.bloodbank.service.RequestService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Blood request screens, including the three workflow actions.
 *
 * <p>The controller never decides whether a transition is allowed. It passes the
 * acting user to {@link RequestService} and translates whatever comes back into a
 * message, so that the browser and the REST API are governed by exactly the same
 * rules.
 */
@Controller
public class RequestController {

    private final RequestService requests;
    private final DonorService donors;

    public RequestController(RequestService requests, DonorService donors) {
        this.requests = requests;
        this.donors = donors;
    }

    @GetMapping("/requests")
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String group,
                       Model model) {
        model.addAttribute("requests", requests.search(status, group));
        model.addAttribute("status", status);
        model.addAttribute("group", group);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("groups", BloodGroup.values());
        return "requests/list";
    }

    @GetMapping("/requests/new")
    public String newForm(Model model) {
        model.addAttribute("request", new RequestForm());
        model.addAttribute("groups", BloodGroup.values());
        model.addAttribute("urgencies", Urgency.values());
        return "requests/form";
    }

    @PostMapping("/requests")
    public String create(@Valid @ModelAttribute("request") RequestForm form,
                         BindingResult binding, Authentication authentication,
                         Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("groups", BloodGroup.values());
            model.addAttribute("urgencies", Urgency.values());
            return "requests/form";
        }
        BloodRequest saved = requests.create(form.toRequest(), CurrentActor.from(authentication));
        flash.addFlashAttribute("message",
                "Request recorded for " + saved.getPatientName() + " (" + saved.getBloodGroup().getLabel() + ")");
        return "redirect:/requests/" + saved.getId();
    }

    /** Request detail, with the ranked shortlist produced by the matching engine. */
    @GetMapping("/requests/{id}")
    public String detail(@PathVariable Long id, Model model) {
        BloodRequest request = requests.findById(id);
        model.addAttribute("request", request);
        model.addAttribute("today", donors.today());
        model.addAttribute("allowedNext", request.allowedNext());
        if (request.getStatus() == RequestStatus.NEW) {
            model.addAttribute("matches", requests.matchesFor(id));
        }
        return "requests/detail";
    }

    @PostMapping("/requests/{id}/match")
    public String match(@PathVariable Long id, @RequestParam Long donorId,
                        Authentication authentication, RedirectAttributes flash) {
        try {
            BloodRequest matched = requests.assignDonor(id, donorId, CurrentActor.from(authentication));
            flash.addFlashAttribute("message",
                    matched.getMatchedDonor().getFullName() + " assigned to this request");
        } catch (RequestService.InvalidMatchException | RequestService.IllegalTransitionException
                 | RequestService.InsufficientRoleException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/requests/" + id;
    }

    @PostMapping("/requests/{id}/fulfil")
    public String fulfil(@PathVariable Long id, Authentication authentication,
                         RedirectAttributes flash) {
        try {
            BloodRequest fulfilled = requests.fulfil(id, CurrentActor.from(authentication));
            flash.addFlashAttribute("message",
                    "Donation confirmed. %s is now ineligible until %s."
                            .formatted(fulfilled.getMatchedDonor().getFullName(),
                                       fulfilled.getMatchedDonor().nextEligibleDate(donors.today())));
        } catch (RequestService.IllegalTransitionException | RequestService.InsufficientRoleException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/requests/" + id;
    }

    @PostMapping("/requests/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam String reason,
                         Authentication authentication, RedirectAttributes flash) {
        try {
            requests.reject(id, reason, CurrentActor.from(authentication));
            flash.addFlashAttribute("message", "Request rejected");
        } catch (RequestService.IllegalTransitionException | RequestService.InsufficientRoleException
                 | IllegalArgumentException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/requests/" + id;
    }
}
