package com.example.lab05.controller;

import com.example.lab05.dto.DashboardResponse;
import com.example.lab05.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/55-3643/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{personName}")
    public DashboardResponse getDashboard(@PathVariable String personName) {
        return dashboardService.getDashboard(personName);
    }
}