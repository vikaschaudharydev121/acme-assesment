package com.acme.salary.salary;

import java.time.Instant;

public record SalaryHistoryResponse(
        Long id,
        Long previousSalary,
        String previousCurrency,
        long newSalary,
        String newCurrency,
        String reason,
        Instant changedAt,
        String changedBy) {

    static SalaryHistoryResponse from(SalaryHistory entry) {
        return new SalaryHistoryResponse(entry.getId(), entry.getPreviousSalary(), entry.getPreviousCurrency(),
                entry.getNewSalary(), entry.getNewCurrency(), entry.getReason(), entry.getCreatedAt(),
                entry.getCreatedBy());
    }
}
