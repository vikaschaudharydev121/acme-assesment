package com.acme.salary.insights;

import com.acme.salary.common.error.InvalidFieldException;
import com.acme.salary.insights.InsightsResponses.CountryInsight;
import com.acme.salary.insights.InsightsResponses.GroupBreakdown;
import com.acme.salary.insights.InsightsResponses.OrgSummary;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsightsServiceTest {

    private static final Country US = new Country("US", "United States", "USD", 1.0);
    private static final Country IN = new Country("IN", "India", "INR", 0.01);

    /** 3 US engineers, 1 US sales rep, 2 Indian engineers. */
    private static final List<PayRecord> RECORDS = List.of(
            new PayRecord("US", "Engineering", "Software Engineer", 100_000),
            new PayRecord("US", "Engineering", "Software Engineer", 120_000),
            new PayRecord("US", "Engineering", "Staff Engineer", 200_000),
            new PayRecord("US", "Sales", "Sales Representative", 60_000),
            new PayRecord("IN", "Engineering", "Software Engineer", 2_000_000),
            new PayRecord("IN", "Engineering", "Software Engineer", 3_000_000));

    @Mock
    private PayRecordRepository repository;
    @Mock
    private CountryCatalog countries;

    private InsightsService service;

    @BeforeEach
    void setUp() {
        service = new InsightsService(repository, countries);
        when(repository.findAllPayRecords()).thenReturn(RECORDS);
        when(repository.findPayRecordsByCountry("IN")).thenReturn(RECORDS.subList(4, 6));
        when(countries.find(anyString())).thenReturn(Optional.empty());
        when(countries.find("US")).thenReturn(Optional.of(US));
        when(countries.find("IN")).thenReturn(Optional.of(IN));
        when(countries.require("country", "IN")).thenReturn(IN);
    }

    @Test
    void summaryNormalisesEverythingToUsd() {
        OrgSummary summary = service.summary();

        assertThat(summary.headcount()).isEqualTo(6);
        assertThat(summary.countries()).isEqualTo(2);
        assertThat(summary.departments()).isEqualTo(2);
        assertThat(summary.jobTitles()).isEqualTo(3);
        // USD: 100k, 120k, 200k, 60k, 20k, 30k
        assertThat(summary.totalPayrollUsd()).isEqualTo(530_000);
        assertThat(summary.salaryUsd().median()).isEqualTo(80_000);
        assertThat(summary.salaryUsd().min()).isEqualTo(20_000);
    }

    @Test
    void byCountryReportsLocalCurrencyStatsLargestCountryFirst() {
        List<CountryInsight> result = service.byCountry();

        assertThat(result).extracting(CountryInsight::countryCode).containsExactly("US", "IN");
        CountryInsight india = result.get(1);
        assertThat(india.currency()).isEqualTo("INR");
        assertThat(india.salaryLocal().median()).isEqualTo(2_500_000);
        assertThat(india.medianUsd()).isEqualTo(25_000);
        assertThat(india.totalPayrollUsd()).isEqualTo(50_000);
    }

    @Test
    void departmentsAcrossCountriesAreComparedInUsd() {
        GroupBreakdown result = service.byDepartment(null);

        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.countryCode()).isNull();
        assertThat(result.groups()).extracting(InsightsResponses.GroupInsight::name)
                .containsExactly("Engineering", "Sales");
        // Engineering USD: 20k, 30k, 100k, 120k, 200k -> median 100k
        assertThat(result.groups().get(0).salary().median()).isEqualTo(100_000);
    }

    @Test
    void jobTitlesWithinACountryStayInLocalCurrency() {
        GroupBreakdown result = service.byJobTitle("IN");

        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.groups()).singleElement().satisfies(group -> {
            assertThat(group.name()).isEqualTo("Software Engineer");
            assertThat(group.salary().count()).isEqualTo(2);
            assertThat(group.salary().median()).isEqualTo(2_500_000);
        });
    }

    @Test
    void unknownCountryFilterIsAValidationError() {
        when(countries.require("country", "ZZ"))
                .thenThrow(new InvalidFieldException("country", "UNKNOWN_COUNTRY", "Unsupported country: ZZ"));

        assertThatThrownBy(() -> service.byDepartment("ZZ")).isInstanceOf(InvalidFieldException.class);
    }
}
