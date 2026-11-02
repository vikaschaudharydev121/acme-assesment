package com.acme.salary.employee;

import com.acme.salary.employee.api.EmployeeResponse;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryCatalog;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    private final CountryCatalog countries;

    public EmployeeMapper(CountryCatalog countries) {
        this.countries = countries;
    }

    public EmployeeResponse toResponse(Employee e) {
        Country country = countries.find(e.getCountryCode())
                .orElseThrow(() -> new IllegalStateException("Employee references unknown country " + e.getCountryCode()));
        return new EmployeeResponse(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                e.getEmail(),
                e.getJobTitle(),
                e.getDepartment(),
                country.getCode(),
                country.getName(),
                country.getCurrency(),
                e.getAnnualSalary(),
                country.toUsd(e.getAnnualSalary()),
                e.getHireDate(),
                e.getVersion(),
                e.getUpdatedAt(),
                e.getUpdatedBy());
    }
}
