package com.acme.salary.insights;

import com.acme.salary.insights.InsightsResponses.CountryInsight;
import com.acme.salary.insights.InsightsResponses.GroupBreakdown;
import com.acme.salary.insights.InsightsResponses.GroupInsight;
import com.acme.salary.insights.InsightsResponses.OrgSummary;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

/**
 * Answers "how do we pay people?". Loads a narrow projection and aggregates in memory: at 10k rows
 * this is a few milliseconds, gives exact medians/percentiles (which SQLite cannot compute), and keeps
 * the maths in a pure, unit-tested class. See docs/04-performance.md for the scaling path.
 */
@Service
@Transactional(readOnly = true)
public class InsightsService {

    static final String USD = "USD";

    private final PayRecordRepository repository;
    private final CountryCatalog countries;

    public InsightsService(PayRecordRepository repository, CountryCatalog countries) {
        this.repository = repository;
        this.countries = countries;
    }

    public OrgSummary summary() {
        List<PayRecord> records = repository.findAllPayRecords();
        long[] usd = records.stream().mapToLong(this::toUsd).toArray();
        SalaryStats stats = SalaryStats.of(usd);
        return new OrgSummary(
                records.size(),
                distinct(records, PayRecord::countryCode),
                distinct(records, PayRecord::department),
                distinct(records, PayRecord::jobTitle),
                stats.total(),
                stats);
    }

    public List<CountryInsight> byCountry() {
        Map<String, List<PayRecord>> byCountry = repository.findAllPayRecords().stream()
                .collect(Collectors.groupingBy(PayRecord::countryCode));
        return byCountry.entrySet().stream()
                .map(entry -> countryInsight(country(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt((CountryInsight c) -> c.salaryLocal().count()).reversed()
                        .thenComparing(CountryInsight::countryName))
                .toList();
    }

    public GroupBreakdown byDepartment(String countryCode) {
        return breakdown(countryCode, PayRecord::department);
    }

    public GroupBreakdown byJobTitle(String countryCode) {
        return breakdown(countryCode, PayRecord::jobTitle);
    }

    /**
     * Groups by the given key. Within one country figures stay in local currency (what HR negotiates in);
     * across countries they are normalised to USD so groups are comparable.
     */
    private GroupBreakdown breakdown(String countryCode, Function<PayRecord, String> groupKey) {
        boolean filtered = hasText(countryCode);
        Country country = filtered ? countries.require("country", countryCode) : null;
        List<PayRecord> records = filtered
                ? repository.findPayRecordsByCountry(country.getCode())
                : repository.findAllPayRecords();
        ToLongFunction<PayRecord> amount = filtered ? PayRecord::annualSalary : this::toUsd;

        List<GroupInsight> groups = records.stream()
                .collect(Collectors.groupingBy(groupKey))
                .entrySet().stream()
                .map(e -> new GroupInsight(e.getKey(), SalaryStats.of(e.getValue().stream().mapToLong(amount).toArray())))
                .sorted(Comparator.comparingLong((GroupInsight g) -> g.salary().median()).reversed()
                        .thenComparing(GroupInsight::name))
                .toList();
        return new GroupBreakdown(filtered ? country.getCode() : null, filtered ? country.getCurrency() : USD, groups);
    }

    private CountryInsight countryInsight(Country country, List<PayRecord> records) {
        SalaryStats local = SalaryStats.of(records.stream().mapToLong(PayRecord::annualSalary).toArray());
        return new CountryInsight(country.getCode(), country.getName(), country.getCurrency(), country.getUsdRate(),
                local, country.toUsd(local.median()), country.toUsd(local.mean()), country.toUsd(local.total()));
    }

    private long toUsd(PayRecord record) {
        return country(record.countryCode()).toUsd(record.annualSalary());
    }

    private Country country(String code) {
        return countries.find(code)
                .orElseThrow(() -> new IllegalStateException("Employee references unknown country " + code));
    }

    private static int distinct(List<PayRecord> records, Function<PayRecord, String> key) {
        return (int) records.stream().map(key).distinct().count();
    }
}
