package com.bloodbank.web.api;

import com.bloodbank.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard snapshot as JSON (FR-22, FR-23) — also used by {@code scripts/health-check.sh}. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApi {

    private final DashboardService dashboard;

    public DashboardApi(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public DashboardService.Snapshot snapshot() {
        return dashboard.snapshot();
    }
}
