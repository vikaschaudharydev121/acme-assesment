package com.acme.salary.employee.api;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        String department,
        String countryCode,
        String countryName,
        String currency,
        long annualSalary,
        long annualSalaryUsd,
        LocalDate hireDate,
        Long version,
        Instant updatedAt,
        String updatedBy) {
}
