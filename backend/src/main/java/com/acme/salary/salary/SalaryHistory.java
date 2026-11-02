package com.acme.salary.salary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** One compensation change. Append-only: Hibernate will never issue an UPDATE for it. */
@Entity
@Immutable
@Table(name = "salary_history")
@EntityListeners(AuditingEntityListener.class)
public class SalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "previous_salary")
    private Long previousSalary;

    @Column(name = "previous_currency")
    private String previousCurrency;

    @Column(name = "new_salary", nullable = false)
    private long newSalary;

    @Column(name = "new_currency", nullable = false)
    private String newCurrency;

    @Column(name = "reason", nullable = false)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    protected SalaryHistory() {
    }

    SalaryHistory(Long employeeId, Money previous, Money current, String reason) {
        this.employeeId = employeeId;
        this.previousSalary = previous == null ? null : previous.amount();
        this.previousCurrency = previous == null ? null : previous.currency();
        this.newSalary = current.amount();
        this.newCurrency = current.currency();
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Long getPreviousSalary() {
        return previousSalary;
    }

    public String getPreviousCurrency() {
        return previousCurrency;
    }

    public long getNewSalary() {
        return newSalary;
    }

    public String getNewCurrency() {
        return newCurrency;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
