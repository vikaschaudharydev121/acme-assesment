package com.acme.salary.employee;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues human-friendly codes (EMP-000123) from a single-row counter. SQLite serialises writers,
 * so the UPDATE ... RETURNING is atomic, and codes are never reused after a deletion.
 */
@Component
public class EmployeeCodeGenerator {

    private final JdbcTemplate jdbc;

    public EmployeeCodeGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        Long value = jdbc.queryForObject(
                "UPDATE employee_code_sequence SET next_value = next_value + 1 WHERE id = 1 RETURNING next_value",
                Long.class);
        return format(value);
    }

    public static String format(long value) {
        return "EMP-%06d".formatted(value);
    }
}
