package com.acme.salary.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.seed.enabled=true", "app.seed.employee-count=1000"})
@ActiveProfiles("test")
class DatabaseSeederIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DatabaseSeeder seeder;

    @Test
    void seedsEmployeesWithInitialHistoryAndAdvancesTheCodeSequence() {
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(1000);
        assertThat(count("SELECT COUNT(*) FROM salary_history WHERE reason = 'Initial salary'")).isEqualTo(1000);
        assertThat(count("SELECT next_value FROM employee_code_sequence")).isEqualTo(1000);
        assertThat(count("SELECT COUNT(DISTINCT country_code) FROM employee")).isEqualTo(8);
    }

    @Test
    void isIdempotentWhenDataAlreadyExists() throws Exception {
        seeder.run(new DefaultApplicationArguments());

        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(1000);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
