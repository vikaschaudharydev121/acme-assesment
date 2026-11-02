package com.acme.salary.insights;

import java.util.List;

/** Response shapes for the insights API. */
public final class InsightsResponses {

    private InsightsResponses() {
    }

    /** Org-wide picture; all money in USD. */
    public record OrgSummary(int headcount, int countries, int departments, int jobTitles,
                             long totalPayrollUsd, SalaryStats salaryUsd) {
    }

    /** Local-currency stats answer "what do we pay in India?"; USD figures allow cross-country comparison. */
    public record CountryInsight(String countryCode, String countryName, String currency, double usdRate,
                                 SalaryStats salaryLocal, long medianUsd, long meanUsd, long totalPayrollUsd) {
    }

    /** A group (department or job title) within a currency. */
    public record GroupInsight(String name, SalaryStats salary) {
    }

    /**
     * @param currency    currency of every figure in {@code groups}: the country's currency when filtered by
     *                    country, otherwise USD
     */
    public record GroupBreakdown(String countryCode, String currency, List<GroupInsight> groups) {
    }
}
