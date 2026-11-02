package com.acme.salary.insights;

/** The narrow slice of an employee that analytics needs — no names, emails or identifiers. */
public record PayRecord(String countryCode, String department, String jobTitle, long annualSalary) {
}
