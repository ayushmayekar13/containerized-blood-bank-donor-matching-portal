package com.bloodbank.web;

import com.bloodbank.config.DemoUsersProperties;
import com.bloodbank.service.DonorService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The donor's own view of their record (requirement FR-09, story US-04).
 *
 * <p>The donor record this login represents comes from configuration, and the
 * controller resolves it from the authenticated username — never from a request
 * parameter. That is what prevents the obvious attack of asking for someone
 * else's profile by changing an id in the URL.
 */
@Controller
public class ProfileController {

    private final DonorService donors;
    private final DemoUsersProperties users;

    public ProfileController(DonorService donors, DemoUsersProperties users) {
        this.donors = donors;
        this.users = users;
    }

    private Long ownDonorId(Authentication authentication) {
        return users.getUsers().stream()
                .filter(account -> account.getUsername().equals(authentication.getName()))
                .map(DemoUsersProperties.Account::getDonorId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "account " + authentication.getName() + " is not linked to a donor record"));
    }

    @GetMapping("/me")
    public String profile(Authentication authentication, Model model) {
        model.addAttribute("today", donors.today());
        try {
            model.addAttribute("donor", donors.findById(ownDonorId(authentication)));
        } catch (IllegalStateException | DonorService.DonorNotFoundException e) {
            // The account is configured but the donor record it points at does not
            // exist - for example after the database volume was recreated with
            // different identifiers. That is a data problem, not a server fault,
            // so the page explains it instead of returning 500.
            model.addAttribute("donor", null);
            model.addAttribute("errorMessage",
                    "Your account is not linked to a donor record yet. Ask the blood bank to link it.");
        }
        return "me";
    }

    @PostMapping("/me/availability")
    public String setAvailability(@RequestParam boolean available,
                                  Authentication authentication, RedirectAttributes flash) {
        // ownDonorId resolves from the authenticated username, never from a
        // request parameter, so a donor cannot toggle someone else's availability
        // by changing an id in the form.
        donors.setAvailability(ownDonorId(authentication), available);
        flash.addFlashAttribute("message", available
                ? "You are now shown as available to be contacted."
                : "You are now shown as unavailable. You will not appear in any shortlist.");
        return "redirect:/me";
    }
}
