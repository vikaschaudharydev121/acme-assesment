package com.acme.salary.seed;

import com.acme.salary.seed.EmployeeDataGenerator.GeneratedEmployee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeDataGeneratorTest {

    private static final Map<String, Double> RATES = Map.of(
            "US", 1.0, "GB", 1.27, "DE", 1.08, "IN", 0.012, "CA", 0.73, "AU", 0.66, "SG", 0.74, "BR", 0.18);
    private static final LocalDate LATEST_HIRE = LocalDate.of(2026, 6, 30);

    private final EmployeeDataGenerator generator = new EmployeeDataGenerator(RATES);

    @Test
    void generatesTheRequestedNumberOfEmployees() {
        assertThat(generator.generate(10_000, 42, LATEST_HIRE)).hasSize(10_000);
    }

    @Test
    void sameSeedProducesIdenticalData() {
        assertThat(generator.generate(500, 7, LATEST_HIRE)).isEqualTo(generator.generate(500, 7, LATEST_HIRE));
    }

    @Test
    void differentSeedsProduceDifferentData() {
        assertThat(generator.generate(50, 1, LATEST_HIRE)).isNotEqualTo(generator.generate(50, 2, LATEST_HIRE));
    }

    @Test
    void codesAndEmailsAreUnique() {
        List<GeneratedEmployee> employees = generator.generate(10_000, 42, LATEST_HIRE);

        assertThat(employees).extracting(GeneratedEmployee::employeeCode).doesNotHaveDuplicates();
        assertThat(employees).extracting(GeneratedEmployee::email).doesNotHaveDuplicates();
        assertThat(employees.get(0).employeeCode()).isEqualTo("EMP-000001");
    }

    @Test
    void everyRecordIsValidForTheSchema() {
        List<GeneratedEmployee> employees = generator.generate(10_000, 42, LATEST_HIRE);

        assertThat(employees).allSatisfy(e -> {
            assertThat(RATES).containsKey(e.countryCode());
            assertThat(e.annualSalary()).isPositive();
            assertThat(e.hireDate()).isBetween(LocalDate.of(2008, 1, 1), LATEST_HIRE);
            assertThat(e.email()).matches("^[a-z.]+\\.\\d+@acme\\.example$");
        });
    }

    @Test
    void coversAllCountriesAndMultipleDepartments() {
        List<GeneratedEmployee> employees = generator.generate(10_000, 42, LATEST_HIRE);

        Set<String> countries = employees.stream().map(GeneratedEmployee::countryCode).collect(Collectors.toSet());
        Set<String> departments = employees.stream().map(GeneratedEmployee::department).collect(Collectors.toSet());
        assertThat(countries).isEqualTo(RATES.keySet());
        assertThat(departments).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void salariesAreInLocalCurrencyMagnitudes() {
        List<GeneratedEmployee> employees = generator.generate(5_000, 42, LATEST_HIRE);

        double medianIndiaInr = employees.stream().filter(e -> e.countryCode().equals("IN"))
                .mapToLong(GeneratedEmployee::annualSalary).sorted().skip(500).findFirst().orElseThrow();
        double maxUsUsd = employees.stream().filter(e -> e.countryCode().equals("US"))
                .mapToLong(GeneratedEmployee::annualSalary).max().orElseThrow();
        assertThat(medianIndiaInr).isGreaterThan(500_000);
        assertThat(maxUsUsd).isLessThan(400_000);
    }

    @Test
    void emailsAreAsciiSafe() {
        assertThat(EmployeeDataGenerator.email("Lukas", "Müller", 12)).isEqualTo("lukas.mueller.12@acme.example");
        assertThat(EmployeeDataGenerator.email("Kelly", "O'Brien", 3)).isEqualTo("kelly.obrien.3@acme.example");
    }

    @Test
    void rejectsMissingExchangeRate() {
        assertThatThrownBy(() -> new EmployeeDataGenerator(Map.of("US", 1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing USD rate");
    }
}
