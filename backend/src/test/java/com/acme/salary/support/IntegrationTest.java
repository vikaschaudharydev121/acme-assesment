package com.acme.salary.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Full application context over a throwaway SQLite file. All subclasses share one cached context;
 * employee data is wiped before each test so tests are independent and order-insensitive.
 *
 * <p>Note: spring-security-test's {@code jwt()} marks requests as CSRF-exempt, so CSRF behaviour is
 * verified over real HTTP in {@code BrowserSessionHttpTest} instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM employee");
    }

    protected static JwtRequestPostProcessor manager() {
        return jwt().jwt(j -> j.subject("manager")).authorities(new SimpleGrantedAuthority("ROLE_HR_MANAGER"));
    }

    protected static JwtRequestPostProcessor viewer() {
        return jwt().jwt(j -> j.subject("viewer")).authorities(new SimpleGrantedAuthority("ROLE_HR_VIEWER"));
    }

    protected String toJson(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected static Map<String, Object> employee(String firstName, String lastName, String email, String country,
                                                  long salary) {
        return Map.of(
                "firstName", firstName,
                "lastName", lastName,
                "email", email,
                "jobTitle", "Software Engineer",
                "department", "Engineering",
                "countryCode", country,
                "annualSalary", salary,
                "hireDate", "2022-03-01");
    }
}
