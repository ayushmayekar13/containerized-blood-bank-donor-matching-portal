package com.bloodbank.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing page of the skeleton application (Task 4).
 *
 * <p>Its purpose is to prove that the WAR builds, deploys and serves a page in
 * every target environment before any domain logic exists, so that later tasks
 * debug one thing at a time. It is replaced by the real dashboard in Task 6.
 */
@Controller
public class HomeController {

    private final String version;

    public HomeController(@Value("${info.app.version:1.0.0}") String version) {
        this.version = version;
    }

    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("title", "Blood Bank Donor Matching Portal");
        model.addAttribute("student", "Ayush Mayekar");
        model.addAttribute("roll", "23102A0018");
        model.addAttribute("version", version);
        return "home";
    }
}
