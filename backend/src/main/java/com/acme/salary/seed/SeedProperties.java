package com.acme.salary.seed;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param enabled       seed on startup when the employee table is empty
 * @param reset         delete all employees first (demo environments only)
 * @param employeeCount number of employees to generate
 * @param randomSeed    fixed seed so every environment gets identical data
 */
@Validated
@ConfigurationProperties("app.seed")
public record SeedProperties(boolean enabled, boolean reset,
                             @Min(1) @Max(1_000_000) int employeeCount,
                             long randomSeed) {
}
