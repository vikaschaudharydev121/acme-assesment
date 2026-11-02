package com.acme.salary.seed;

import com.acme.salary.employee.EmployeeCodeGenerator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.ToIntFunction;

/**
 * Pure, deterministic generator of realistic employee data: the same seed always yields the same
 * 10,000 employees, so demos, screenshots and tests are reproducible.
 *
 * <p>Salary model: a US-market base per job title, scaled by a per-country labour-cost factor,
 * converted to local currency and given ±15% individual variation, rounded to the nearest 100.
 */
public final class EmployeeDataGenerator {

    public record GeneratedEmployee(String employeeCode, String firstName, String lastName, String email,
                                    String jobTitle, String department, String countryCode,
                                    long annualSalary, LocalDate hireDate) {
    }

    private record Job(String title, String department, long usBaseSalaryUsd, int weight) {
    }

    private record CountryProfile(String code, double labourCostFactor, int weight) {
    }

    static final String EMAIL_DOMAIN = "acme.example";

    private static final List<Job> JOBS = List.of(
            new Job("Software Engineer", "Engineering", 125_000, 22),
            new Job("Senior Software Engineer", "Engineering", 165_000, 12),
            new Job("Staff Engineer", "Engineering", 215_000, 3),
            new Job("Engineering Manager", "Engineering", 205_000, 3),
            new Job("QA Engineer", "Engineering", 98_000, 5),
            new Job("DevOps Engineer", "Engineering", 140_000, 4),
            new Job("Data Analyst", "Data", 92_000, 5),
            new Job("Data Scientist", "Data", 150_000, 3),
            new Job("Product Manager", "Product", 158_000, 4),
            new Job("Product Designer", "Product", 128_000, 3),
            new Job("Sales Representative", "Sales", 72_000, 8),
            new Job("Account Executive", "Sales", 115_000, 5),
            new Job("Marketing Specialist", "Marketing", 78_000, 4),
            new Job("Customer Support Specialist", "Customer Success", 55_000, 8),
            new Job("Customer Success Manager", "Customer Success", 92_000, 3),
            new Job("HR Specialist", "People", 72_000, 2),
            new Job("Recruiter", "People", 78_000, 2),
            new Job("Accountant", "Finance", 82_000, 2),
            new Job("Financial Analyst", "Finance", 96_000, 2),
            new Job("Operations Manager", "Operations", 118_000, 1),
            new Job("Director", "Leadership", 260_000, 1));

    private static final List<CountryProfile> COUNTRIES = List.of(
            new CountryProfile("US", 1.00, 30),
            new CountryProfile("IN", 0.28, 25),
            new CountryProfile("GB", 0.72, 10),
            new CountryProfile("DE", 0.75, 10),
            new CountryProfile("CA", 0.78, 8),
            new CountryProfile("BR", 0.32, 7),
            new CountryProfile("AU", 0.80, 5),
            new CountryProfile("SG", 0.85, 5));

    private static final String[] FIRST_NAMES = {
            "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda", "David", "Elizabeth",
            "Aarav", "Ananya", "Vivaan", "Diya", "Arjun", "Isha", "Rohan", "Priya", "Kabir", "Meera",
            "Oliver", "Amelia", "George", "Isla", "Harry", "Ava", "Lukas", "Mia", "Felix", "Hannah",
            "Liam", "Emma", "Noah", "Olivia", "Lucas", "Sofia", "Mateus", "Beatriz", "Gabriel", "Julia",
            "Wei", "Mei", "Jun", "Hui", "Ethan", "Chloe", "Jack", "Ruby", "Thomas", "Grace",
            "Fatima", "Omar", "Aisha", "Yusuf", "Leila", "Samuel", "Zara", "Daniel", "Nora", "Ahmed"};

    private static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Wilson", "Taylor",
            "Sharma", "Patel", "Iyer", "Reddy", "Gupta", "Nair", "Singh", "Kumar", "Das", "Menon",
            "Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Becker", "Clarke", "Evans", "Hughes", "Walker",
            "Silva", "Santos", "Oliveira", "Souza", "Costa", "Pereira", "Tan", "Lim", "Ng", "Wong",
            "Martin", "Thompson", "White", "Harris", "Lee", "O'Brien", "Kelly", "Murphy", "Campbell", "Stewart",
            "Khan", "Ali", "Hassan", "Rahman", "Nguyen", "Tremblay", "Roy", "Gagnon", "Anderson", "Thomas"};

    private static final LocalDate EARLIEST_HIRE = LocalDate.of(2008, 1, 1);

    private final Map<String, Double> usdRates;

    /** @param usdRates country code → value of 1 local unit in USD (from the reference table) */
    public EmployeeDataGenerator(Map<String, Double> usdRates) {
        this.usdRates = Map.copyOf(usdRates);
        COUNTRIES.forEach(c -> {
            if (!this.usdRates.containsKey(c.code())) {
                throw new IllegalArgumentException("Missing USD rate for " + c.code());
            }
        });
    }

    /** @param latestHire upper bound for hire dates; pass a fixed date for fully reproducible output */
    public List<GeneratedEmployee> generate(int count, long seed, LocalDate latestHire) {
        Random random = new Random(seed);
        long hireRangeDays = ChronoUnit.DAYS.between(EARLIEST_HIRE, latestHire);
        List<GeneratedEmployee> employees = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String first = pick(random, FIRST_NAMES);
            String last = pick(random, LAST_NAMES);
            Job job = weighted(random, JOBS, Job::weight);
            CountryProfile country = weighted(random, COUNTRIES, CountryProfile::weight);
            employees.add(new GeneratedEmployee(
                    EmployeeCodeGenerator.format(i),
                    first,
                    last,
                    email(first, last, i),
                    job.title(),
                    job.department(),
                    country.code(),
                    salary(random, job, country),
                    EARLIEST_HIRE.plusDays((long) (random.nextDouble() * hireRangeDays))));
        }
        return employees;
    }

    private long salary(Random random, Job job, CountryProfile country) {
        double variation = 0.85 + random.nextDouble() * 0.30;
        double usd = job.usBaseSalaryUsd() * country.labourCostFactor() * variation;
        double local = usd / usdRates.get(country.code());
        return Math.max(100, Math.round(local / 100.0) * 100);
    }

    static String email(String first, String last, int sequence) {
        String local = (first + "." + last).toLowerCase(Locale.ROOT)
                .replace("ü", "ue")
                .replaceAll("[^a-z.]", "");
        return "%s.%d@%s".formatted(local, sequence, EMAIL_DOMAIN);
    }

    private static String pick(Random random, String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static <T> T weighted(Random random, List<T> items, ToIntFunction<T> weight) {
        int total = items.stream().mapToInt(weight).sum();
        int roll = random.nextInt(total);
        for (T item : items) {
            roll -= weight.applyAsInt(item);
            if (roll < 0) {
                return item;
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
