package com.acme.salary.employee;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.springframework.util.StringUtils.hasText;

/** Builds the search query with bound parameters only — user input never reaches SQL text. */
final class EmployeeSpecifications {

    private static final char ESCAPE = '\\';

    private EmployeeSpecifications() {
    }

    static Specification<Employee> matching(EmployeeSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(criteria.query())) {
                predicates.add(freeText(root, cb, criteria.query()));
            }
            if (hasText(criteria.countryCode())) {
                predicates.add(cb.equal(root.get("countryCode"), criteria.countryCode().trim()));
            }
            if (hasText(criteria.department())) {
                predicates.add(cb.equal(root.get("department"), criteria.department().trim()));
            }
            if (hasText(criteria.jobTitle())) {
                predicates.add(cb.equal(root.get("jobTitle"), criteria.jobTitle().trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Matches name, "first last", email or employee code, case-insensitively. */
    private static Predicate freeText(Root<Employee> root, CriteriaBuilder cb, String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        Expression<String> fullName = cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"));
        return cb.or(
                cb.like(cb.lower(fullName), pattern, ESCAPE),
                cb.like(cb.lower(root.get("email")), pattern, ESCAPE),
                cb.like(cb.lower(root.get("employeeCode")), pattern, ESCAPE));
    }

    /** Treat %, _ and the escape char typed by a user as literals rather than wildcards. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
