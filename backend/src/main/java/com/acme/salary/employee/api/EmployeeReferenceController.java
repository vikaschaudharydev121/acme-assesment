package com.acme.salary.employee.api;

import com.acme.salary.employee.EmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Values currently in use, for filter drop-downs and form autocomplete. */
@RestController
@RequestMapping("/api/v1/reference")
class EmployeeReferenceController {

    private final EmployeeService service;

    EmployeeReferenceController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping("/departments")
    List<String> departments() {
        return service.departments();
    }

    @GetMapping("/job-titles")
    List<String> jobTitles() {
        return service.jobTitles();
    }
}
