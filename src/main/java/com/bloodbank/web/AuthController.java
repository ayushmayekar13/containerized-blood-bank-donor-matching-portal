package com.bloodbank.web;

import com.bloodbank.config.DemoUsersProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The login screen.
 *
 * <p>The page lists the demonstration accounts and what each role may do, because
 * the role-based workflow is the thing being demonstrated and an examiner should
 * not have to be told the passwords out of band. These are credentials for a
 * locally deployed demonstration instance only.
 */
@Controller
public class AuthController {

    private final DemoUsersProperties users;

    public AuthController(DemoUsersProperties users) {
        this.users = users;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("accounts", users.getUsers());
        return "login";
    }
}
