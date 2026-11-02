package com.acme.salary.employee;

import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.InvalidFieldException;
import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.common.web.PageResponse;
import com.acme.salary.employee.api.EmployeeRequest;
import com.acme.salary.employee.api.EmployeeResponse;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryCatalog;
import com.acme.salary.salary.Money;
import com.acme.salary.salary.SalaryHistoryResponse;
import com.acme.salary.salary.SalaryHistoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.springframework.util.StringUtils.hasText;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    /** Whitelist: sorting is the only place a client-supplied identifier influences the query. */
    static final Set<String> SORTABLE_FIELDS = Set.of(
            "employeeCode", "firstName", "lastName", "email", "jobTitle", "department",
            "countryCode", "annualSalary", "hireDate");

    private static final Sort DEFAULT_SORT = Sort.by("lastName", "firstName");

    private final EmployeeRepository repository;
    private final EmployeeCodeGenerator codeGenerator;
    private final SalaryHistoryService salaryHistory;
    private final CountryCatalog countries;
    private final EmployeeMapper mapper;

    public EmployeeService(EmployeeRepository repository,
                           EmployeeCodeGenerator codeGenerator,
                           SalaryHistoryService salaryHistory,
                           CountryCatalog countries,
                           EmployeeMapper mapper) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.salaryHistory = salaryHistory;
        this.countries = countries;
        this.mapper = mapper;
    }

    public PageResponse<EmployeeResponse> search(EmployeeSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                repository.findAll(EmployeeSpecifications.matching(criteria), withStableSort(pageable)),
                mapper::toResponse);
    }

    public EmployeeResponse get(long id) {
        return mapper.toResponse(find(id));
    }

    public List<SalaryHistoryResponse> salaryHistory(long id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        return salaryHistory.historyFor(id);
    }

    public List<String> departments() {
        return repository.findDistinctDepartments();
    }

    public List<String> jobTitles() {
        return repository.findDistinctJobTitles();
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        Country country = countries.require("countryCode", request.countryCode());
        EmployeeDetails details = normalise(request);
        if (repository.existsByEmailIgnoreCase(details.email())) {
            throw emailTaken();
        }
        Employee employee = repository.save(new Employee(codeGenerator.next(), details, request.annualSalary()));
        salaryHistory.recordInitial(employee.getId(), new Money(employee.getAnnualSalary(), country.getCurrency()));
        return mapper.toResponse(employee);
    }

    @Transactional
    public EmployeeResponse update(long id, EmployeeRequest request) {
        if (request.version() == null) {
            throw new InvalidFieldException("version", "VERSION_REQUIRED", "version is required when updating");
        }
        Employee employee = find(id);
        if (!Objects.equals(employee.getVersion(), request.version())) {
            throw new ConflictException("STALE_VERSION",
                    "This record was changed by someone else. Reload it and try again.");
        }
        Country newCountry = countries.require("countryCode", request.countryCode());
        EmployeeDetails details = normalise(request);
        if (repository.existsByEmailIgnoreCaseAndIdNot(details.email(), id)) {
            throw emailTaken();
        }

        Money previous = new Money(employee.getAnnualSalary(), currencyOf(employee.getCountryCode()));
        Money current = new Money(request.annualSalary(), newCountry.getCurrency());
        boolean compensationChanged = !previous.equals(current);
        if (compensationChanged && !hasText(request.salaryChangeReason())) {
            throw new InvalidFieldException("salaryChangeReason", "REASON_REQUIRED",
                    "A reason is required when salary or currency changes");
        }

        employee.apply(details);
        employee.changeSalary(current.amount());
        // Flush now so the response carries the incremented version and fresh audit columns.
        Employee saved = repository.saveAndFlush(employee);
        if (compensationChanged) {
            salaryHistory.recordChange(id, previous, current, request.salaryChangeReason().trim());
        }
        return mapper.toResponse(saved);
    }

    @Transactional
    public void delete(long id) {
        repository.delete(find(id));
    }

    private Employee find(long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private String currencyOf(String countryCode) {
        return countries.find(countryCode).map(Country::getCurrency).orElse("???");
    }

    /** Validates requested sort fields and appends id so paging is deterministic across equal keys. */
    static Pageable withStableSort(Pageable pageable) {
        Sort requested = pageable.getSort();
        for (Sort.Order order : requested) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidFieldException("sort", "UNSUPPORTED_SORT",
                        "Cannot sort by '" + order.getProperty() + "'");
            }
        }
        Sort sort = (requested.isSorted() ? requested : DEFAULT_SORT).and(Sort.by("id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private static EmployeeDetails normalise(EmployeeRequest request) {
        return new EmployeeDetails(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email().trim().toLowerCase(Locale.ROOT),
                request.jobTitle().trim(),
                request.department().trim(),
                request.countryCode(),
                request.hireDate());
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("Employee " + id + " was not found");
    }

    private static ConflictException emailTaken() {
        return new ConflictException("EMAIL_ALREADY_EXISTS", "Another employee already uses this email address");
    }
}
