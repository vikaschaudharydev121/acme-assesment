package com.acme.salary.employee.api;

import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.EmployeeSearchCriteria;
import com.acme.salary.employee.EmployeeService;
import com.acme.salary.salary.SalaryHistoryResponse;
import com.acme.salary.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Read endpoints are open to every authenticated HR role; writes require HR_MANAGER. */
@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController {

    private final EmployeeService service;

    EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<EmployeeResponse> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String country,
            @RequestParam(required = false) @Size(max = 100) String department,
            @RequestParam(required = false) @Size(max = 100) String jobTitle,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.search(new EmployeeSearchCriteria(q, country, department, jobTitle), pageable);
    }

    @GetMapping("/{id}")
    EmployeeResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/salary-history")
    List<SalaryHistoryResponse> salaryHistory(@PathVariable long id) {
        return service.salaryHistory(id);
    }

    @PostMapping
    @PreAuthorize(Roles.CAN_MANAGE_EMPLOYEES)
    ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_EMPLOYEES)
    EmployeeResponse update(@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_EMPLOYEES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
