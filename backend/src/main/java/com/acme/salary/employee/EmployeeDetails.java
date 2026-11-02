package com.acme.salary.employee;

import java.time.LocalDate;

/** The editable, non-compensation attributes of an employee, already normalised. */
public record EmployeeDetails(
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        String department,
        String countryCode,
        LocalDate hireDate) {
}
