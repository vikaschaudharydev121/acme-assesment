package com.acme.salary.employee.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body for create and update. Server-owned fields (id, employee code, audit columns) are absent, and
 * unknown JSON properties are rejected globally, so they cannot be mass-assigned.
 *
 * @param version            required on update for optimistic locking; ignored on create
 * @param salaryChangeReason required on update when salary or country (currency) changes
 */
public record EmployeeRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = PERSON_NAME, message = PERSON_NAME_MESSAGE)
        String firstName,

        @NotBlank @Size(max = 100) @Pattern(regexp = PERSON_NAME, message = PERSON_NAME_MESSAGE)
        String lastName,

        @NotBlank @Email @Size(max = 254)
        String email,

        @NotBlank @Size(max = 100) @Pattern(regexp = LABEL, message = LABEL_MESSAGE)
        String jobTitle,

        @NotBlank @Size(max = 100) @Pattern(regexp = LABEL, message = LABEL_MESSAGE)
        String department,

        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO-3166 alpha-2 code")
        String countryCode,

        @NotNull @Positive @Max(value = 1_000_000_000L, message = "must not exceed 1,000,000,000")
        Long annualSalary,

        @NotNull @PastOrPresent
        LocalDate hireDate,

        Long version,

        @Size(max = 500)
        String salaryChangeReason) {

    static final String PERSON_NAME = "^[\\p{L}][\\p{L}\\p{M} .'-]*$";
    static final String PERSON_NAME_MESSAGE = "may contain letters, spaces, apostrophes, periods and hyphens only";
    static final String LABEL = "^[\\p{L}\\p{N}][\\p{L}\\p{N} &/.,'()+-]*$";
    static final String LABEL_MESSAGE = "contains unsupported characters";
}
