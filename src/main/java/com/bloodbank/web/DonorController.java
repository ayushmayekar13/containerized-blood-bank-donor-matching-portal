package com.bloodbank.web;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.Donor;
import com.bloodbank.service.DonorService;
import com.bloodbank.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Donor registry screens.
 *
 * <p>The controller binds input, calls exactly one service method and chooses a
 * view. Every rule it appears to enforce — duplicate phone numbers, eligibility,
 * compatibility — is actually enforced in {@link DonorService} or
 * {@link MatchingService}, so the REST API cannot bypass it.
 */
@Controller
public class DonorController {

    private final DonorService donorService;
    private final MatchingService matchingService;

    public DonorController(DonorService donorService, MatchingService matchingService) {
        this.donorService = donorService;
        this.matchingService = matchingService;
    }

    @GetMapping("/donors")
    public String list(@RequestParam(required = false) String group,
                       @RequestParam(required = false) String city,
                       @RequestParam(defaultValue = "false") boolean availableOnly,
                       Model model) {
        model.addAttribute("donors", donorService.search(group, city, availableOnly));
        model.addAttribute("group", group);
        model.addAttribute("city", city);
        model.addAttribute("availableOnly", availableOnly);
        model.addAttribute("groups", BloodGroup.values());
        model.addAttribute("today", donorService.today());
        return "donors/list";
    }

    @GetMapping("/donors/new")
    public String newForm(Model model) {
        model.addAttribute("donor", new DonorForm());
        model.addAttribute("groups", BloodGroup.values());
        model.addAttribute("mode", "new");
        return "donors/form";
    }

    @PostMapping("/donors")
    public String create(@Valid @org.springframework.web.bind.annotation.ModelAttribute("donor") DonorForm form,
                         BindingResult binding, Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("groups", BloodGroup.values());
            model.addAttribute("mode", "new");
            return "donors/form";
        }
        try {
            Donor saved = donorService.create(form.toDonor());
            flash.addFlashAttribute("message", "Donor " + saved.getFullName() + " registered");
            return "redirect:/donors";
        } catch (DonorService.DuplicatePhoneException e) {
            binding.rejectValue("phone", "duplicate", e.getMessage());
            model.addAttribute("groups", BloodGroup.values());
            model.addAttribute("mode", "new");
            return "donors/form";
        }
    }

    @GetMapping("/donors/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Donor donor = donorService.findById(id);
        model.addAttribute("donor", donor);
        model.addAttribute("today", donorService.today());
        model.addAttribute("canDonateTo", donor.getBloodGroup().canDonateTo());
        return "donors/detail";
    }

    @GetMapping("/donors/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("donor", DonorForm.from(donorService.findById(id)));
        model.addAttribute("donorId", id);
        model.addAttribute("groups", BloodGroup.values());
        model.addAttribute("mode", "edit");
        return "donors/form";
    }

    @PostMapping("/donors/{id}")
    public String update(@PathVariable Long id,
                         @Valid @org.springframework.web.bind.annotation.ModelAttribute("donor") DonorForm form,
                         BindingResult binding, Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("donorId", id);
            model.addAttribute("groups", BloodGroup.values());
            model.addAttribute("mode", "edit");
            return "donors/form";
        }
        try {
            Donor saved = donorService.update(id, form.toDonor());
            flash.addFlashAttribute("message", "Donor " + saved.getFullName() + " updated");
            return "redirect:/donors/" + id;
        } catch (DonorService.DuplicatePhoneException e) {
            binding.rejectValue("phone", "duplicate", e.getMessage());
            model.addAttribute("donorId", id);
            model.addAttribute("groups", BloodGroup.values());
            model.addAttribute("mode", "edit");
            return "donors/form";
        }
    }

    /**
     * Match preview: the ranked shortlist for a recipient group, independent of
     * any recorded request. This is the screen that replaces the phone tree, and
     * in Task 6 the same service call is reused from the request detail page.
     */
    @GetMapping("/match")
    public String matchPreview(@RequestParam(required = false) String group,
                               @RequestParam(required = false) String city,
                               Model model) {
        model.addAttribute("groups", BloodGroup.values());
        model.addAttribute("group", group);
        model.addAttribute("city", city);
        model.addAttribute("today", donorService.today());
        if (group != null && !group.isBlank()) {
            BloodGroup recipient = BloodGroup.fromText(group);
            model.addAttribute("recipient", recipient);
            model.addAttribute("compatibleGroups", recipient.compatibleDonorGroups());
            model.addAttribute("matches", matchingService.findMatches(recipient,
                    (city == null || city.isBlank()) ? null : city.trim()));
        }
        return "match";
    }
}
