package com.acme.salary.insights;

import com.acme.salary.insights.InsightsResponses.CountryInsight;
import com.acme.salary.insights.InsightsResponses.GroupBreakdown;
import com.acme.salary.insights.InsightsResponses.OrgSummary;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/insights")
class InsightsController {

    private static final String COUNTRY_CODE = "^[A-Z]{2}$";

    private final InsightsService service;

    InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    OrgSummary summary() {
        return service.summary();
    }

    @GetMapping("/by-country")
    List<CountryInsight> byCountry() {
        return service.byCountry();
    }

    @GetMapping("/by-department")
    GroupBreakdown byDepartment(@RequestParam(required = false) @Pattern(regexp = COUNTRY_CODE) String country) {
        return service.byDepartment(country);
    }

    @GetMapping("/by-job-title")
    GroupBreakdown byJobTitle(@RequestParam(required = false) @Pattern(regexp = COUNTRY_CODE) String country) {
        return service.byJobTitle(country);
    }
}
