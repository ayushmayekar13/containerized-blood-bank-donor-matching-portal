package com.bloodbank.web;

import com.bloodbank.domain.BloodGroup;
import com.bloodbank.domain.RequestStatus;
import com.bloodbank.service.DashboardService;
import com.bloodbank.service.RequestService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Summary dashboard: supply against demand, with shortage flags (FR-22, FR-23). */
@Controller
public class DashboardController {

    private final DashboardService dashboard;
    private final RequestService requests;

    public DashboardController(DashboardService dashboard, RequestService requests) {
        this.dashboard = dashboard;
        this.requests = requests;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("snapshot", dashboard.snapshot());
        model.addAttribute("groups", BloodGroup.values());
        model.addAttribute("openRequests", requests.search(RequestStatus.NEW.name(), null));
        return "dashboard";
    }
}
