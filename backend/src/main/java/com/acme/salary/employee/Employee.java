package com.acme.salary.employee;

import com.acme.salary.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "employee")
public class Employee extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, updatable = false)
    private String employeeCode;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "department", nullable = false)
    private String department;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    /** Annual gross salary in whole units of the country's currency. */
    @Column(name = "annual_salary", nullable = false)
    private long annualSalary;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Employee() {
    }

    public Employee(String employeeCode, EmployeeDetails details, long annualSalary) {
        this.employeeCode = Objects.requireNonNull(employeeCode);
        apply(details);
        this.annualSalary = annualSalary;
    }

    /** Updates non-compensation fields. Compensation changes go through {@link #changeSalary}. */
    public void apply(EmployeeDetails details) {
        this.firstName = details.firstName();
        this.lastName = details.lastName();
        this.email = details.email();
        this.jobTitle = details.jobTitle();
        this.department = details.department();
        this.countryCode = details.countryCode();
        this.hireDate = details.hireDate();
    }

    public void changeSalary(long newAnnualSalary) {
        this.annualSalary = newAnnualSalary;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getDepartment() {
        return department;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public long getAnnualSalary() {
        return annualSalary;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public Long getVersion() {
        return version;
    }
}
