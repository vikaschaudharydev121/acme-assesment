package com.acme.salary.seed;

import com.acme.salary.common.persistence.JpaAuditingConfig;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryCatalog;
import com.acme.salary.salary.SalaryHistoryService;
import com.acme.salary.seed.EmployeeDataGenerator.GeneratedEmployee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The seed script. Runs on startup when {@code app.seed.enabled=true} and the employee table is
 * empty (idempotent), or always when {@code app.seed.reset=true}. Uses JDBC batch inserts in a single
 * transaction: 10k employees plus their initial salary history load in well under a second.
 */
@Component
class DatabaseSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);
    private static final int BATCH_SIZE = 1_000;
    /** Fixed so the generated data set is identical on every machine and every day. */
    static final LocalDate LATEST_HIRE_DATE = LocalDate.of(2026, 6, 30);

    private final SeedProperties properties;
    private final JdbcTemplate jdbc;
    private final CountryCatalog countries;
    private final Clock clock;

    DatabaseSeeder(SeedProperties properties, JdbcTemplate jdbc, CountryCatalog countries, Clock clock) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.countries = countries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled() && !properties.reset()) {
            return;
        }
        if (properties.reset()) {
            log.warn("app.seed.reset=true: deleting all employees and salary history");
            jdbc.update("DELETE FROM employee");
        } else if (count() > 0) {
            log.info("Employees already present; skipping seed");
            return;
        }
        long started = System.nanoTime();
        seed(properties.employeeCount());
        log.info("Seeded {} employees in {} ms", properties.employeeCount(), (System.nanoTime() - started) / 1_000_000);
    }

    private void seed(int count) {
        var rates = countries.all().stream().collect(Collectors.toMap(Country::getCode, Country::getUsdRate));
        List<GeneratedEmployee> employees = new EmployeeDataGenerator(rates)
                .generate(count, properties.randomSeed(), LATEST_HIRE_DATE);
        long now = clock.millis();
        String actor = JpaAuditingConfig.SYSTEM_ACTOR;

        jdbc.batchUpdate("""
                        INSERT INTO employee (employee_code, first_name, last_name, email, job_title, department,
                                              country_code, annual_salary, hire_date, version,
                                              created_at, created_by, updated_at, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)""",
                employees, BATCH_SIZE, (ps, e) -> {
                    ps.setString(1, e.employeeCode());
                    ps.setString(2, e.firstName());
                    ps.setString(3, e.lastName());
                    ps.setString(4, e.email());
                    ps.setString(5, e.jobTitle());
                    ps.setString(6, e.department());
                    ps.setString(7, e.countryCode());
                    ps.setLong(8, e.annualSalary());
                    ps.setString(9, e.hireDate().toString());
                    ps.setLong(10, now);
                    ps.setString(11, actor);
                    ps.setLong(12, now);
                    ps.setString(13, actor);
                });

        // Every employee starts with one history entry, exactly as if created through the API.
        jdbc.update("""
                INSERT INTO salary_history (employee_id, new_salary, new_currency, reason, created_at, created_by)
                SELECT e.id, e.annual_salary, c.currency, ?, ?, ?
                FROM employee e JOIN country c ON c.code = e.country_code""",
                SalaryHistoryService.INITIAL_SALARY_REASON, now, actor);

        // Continue issuing codes after the seeded range (and never below an existing value).
        jdbc.update("UPDATE employee_code_sequence SET next_value = MAX(next_value, ?) WHERE id = 1", count);
    }

    private long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM employee", Long.class);
        return count == null ? 0 : count;
    }
}
