package com.acme.salary.employee;

/** All filters are optional; blank values mean "no filter". */
public record EmployeeSearchCriteria(String query, String countryCode, String department, String jobTitle) {

    public static EmployeeSearchCriteria none() {
        return new EmployeeSearchCriteria(null, null, null, null);
    }
}
