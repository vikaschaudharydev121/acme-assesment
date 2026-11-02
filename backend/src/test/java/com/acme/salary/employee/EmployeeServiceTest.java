package com.acme.salary.employee;

import com.acme.salary.common.error.ConflictException;
import com.acme.salary.common.error.InvalidFieldException;
import com.acme.salary.common.error.NotFoundException;
import com.acme.salary.employee.api.EmployeeRequest;
import com.acme.salary.employee.api.EmployeeResponse;
import com.acme.salary.reference.Country;
import com.acme.salary.reference.CountryCatalog;
import com.acme.salary.salary.Money;
import com.acme.salary.salary.SalaryHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceTest {

    private static final Country INDIA = new Country("IN", "India", "INR", 0.012);
    private static final Country GERMANY = new Country("DE", "Germany", "EUR", 1.08);

    @Mock
    private EmployeeRepository repository;
    @Mock
    private EmployeeCodeGenerator codeGenerator;
    @Mock
    private SalaryHistoryService salaryHistory;
    @Mock
    private CountryCatalog countries;

    private EmployeeService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(repository, codeGenerator, salaryHistory, countries, new EmployeeMapper(countries));
        for (Country c : new Country[]{INDIA, GERMANY}) {
            when(countries.find(c.getCode())).thenReturn(Optional.of(c));
            when(countries.require("countryCode", c.getCode())).thenReturn(c);
        }
        when(countries.require("countryCode", "ZZ"))
                .thenThrow(new InvalidFieldException("countryCode", "UNKNOWN_COUNTRY", "Unsupported country: ZZ"));
        when(repository.save(any(Employee.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));
        when(repository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Create {

        @Test
        void normalisesInputAssignsCodeAndRecordsInitialSalary() {
            when(codeGenerator.next()).thenReturn("EMP-000123");

            EmployeeResponse created = service.create(request(" Asha ", " Asha.RAO@Acme.Example ", "IN", 2_400_000L, null, null));

            assertThat(created.employeeCode()).isEqualTo("EMP-000123");
            assertThat(created.firstName()).isEqualTo("Asha");
            assertThat(created.email()).isEqualTo("asha.rao@acme.example");
            assertThat(created.currency()).isEqualTo("INR");
            assertThat(created.annualSalaryUsd()).isEqualTo(28_800);
            verify(salaryHistory).recordInitial(7L, new Money(2_400_000L, "INR"));
        }

        @Test
        void rejectsDuplicateEmail() {
            when(repository.existsByEmailIgnoreCase("taken@acme.example")).thenReturn(true);

            assertThatThrownBy(() -> service.create(request("Asha", "taken@acme.example", "IN", 1L, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("EMAIL_ALREADY_EXISTS");
            verify(repository, never()).save(any());
        }

        @Test
        void rejectsUnknownCountry() {
            assertThatThrownBy(() -> service.create(request("Asha", "a@acme.example", "ZZ", 1L, null, null)))
                    .isInstanceOf(InvalidFieldException.class)
                    .extracting("code").isEqualTo("UNKNOWN_COUNTRY");
        }
    }

    @Nested
    class Update {

        private Employee existing;

        @BeforeEach
        void givenExistingEmployee() {
            existing = withId(new Employee("EMP-000007",
                    new EmployeeDetails("Asha", "Rao", "asha@acme.example", "Software Engineer", "Engineering", "IN",
                            LocalDate.of(2022, 1, 1)), 2_400_000L), 7L);
            ReflectionTestUtils.setField(existing, "version", 3L);
            when(repository.findById(7L)).thenReturn(Optional.of(existing));
        }

        @Test
        void salaryChangeIsRecordedWithReason() {
            service.update(7L, request("Asha", "asha@acme.example", "IN", 2_700_000L, 3L, " Annual review "));

            assertThat(existing.getAnnualSalary()).isEqualTo(2_700_000L);
            verify(salaryHistory).recordChange(7L, new Money(2_400_000L, "INR"), new Money(2_700_000L, "INR"),
                    "Annual review");
        }

        @Test
        void salaryChangeWithoutReasonIsRejected() {
            assertThatThrownBy(() -> service.update(7L, request("Asha", "asha@acme.example", "IN", 2_700_000L, 3L, " ")))
                    .isInstanceOf(InvalidFieldException.class)
                    .extracting("field").isEqualTo("salaryChangeReason");
            assertThat(existing.getAnnualSalary()).isEqualTo(2_400_000L);
        }

        @Test
        void relocationIsACompensationChangeEvenIfTheNumberIsUnchanged() {
            service.update(7L, request("Asha", "asha@acme.example", "DE", 2_400_000L, 3L, "Relocation"));

            verify(salaryHistory).recordChange(7L, new Money(2_400_000L, "INR"), new Money(2_400_000L, "EUR"),
                    "Relocation");
        }

        @Test
        void nonCompensationEditNeedsNoReasonAndWritesNoHistory() {
            service.update(7L, request("Asha", "asha.new@acme.example", "IN", 2_400_000L, 3L, null));

            assertThat(existing.getEmail()).isEqualTo("asha.new@acme.example");
            verify(salaryHistory, never()).recordChange(anyLong(), any(), any(), anyString());
        }

        @Test
        void staleVersionIsAConflict() {
            assertThatThrownBy(() -> service.update(7L, request("Asha", "asha@acme.example", "IN", 2_400_000L, 2L, null)))
                    .isInstanceOf(ConflictException.class)
                    .extracting("code").isEqualTo("STALE_VERSION");
        }

        @Test
        void missingVersionIsRejected() {
            assertThatThrownBy(() -> service.update(7L, request("Asha", "asha@acme.example", "IN", 1L, null, null)))
                    .isInstanceOf(InvalidFieldException.class)
                    .extracting("field").isEqualTo("version");
        }

        @Test
        void emailUsedByAnotherEmployeeIsAConflict() {
            when(repository.existsByEmailIgnoreCaseAndIdNot("other@acme.example", 7L)).thenReturn(true);

            assertThatThrownBy(() -> service.update(7L, request("Asha", "other@acme.example", "IN", 2_400_000L, 3L, null)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void unknownEmployeeIsNotFound() {
            assertThatThrownBy(() -> service.update(99L, request("Asha", "a@acme.example", "IN", 1L, 0L, null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Sorting {

        @Test
        void defaultsToNameAndAlwaysAppendsIdForStablePaging() {
            Pageable result = EmployeeService.withStableSort(PageRequest.of(2, 50));

            assertThat(result.getSort()).isEqualTo(Sort.by("lastName", "firstName", "id"));
            assertThat(result.getPageNumber()).isEqualTo(2);
            assertThat(result.getPageSize()).isEqualTo(50);
        }

        @Test
        void keepsRequestedWhitelistedSort() {
            Pageable result = EmployeeService.withStableSort(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "annualSalary")));

            assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Order.desc("annualSalary"), Sort.Order.asc("id")));
        }

        @Test
        void rejectsNonWhitelistedSortField() {
            assertThatThrownBy(() -> EmployeeService.withStableSort(PageRequest.of(0, 20, Sort.by("createdBy"))))
                    .isInstanceOf(InvalidFieldException.class)
                    .extracting("code").isEqualTo("UNSUPPORTED_SORT");
        }
    }

    @Test
    void likeWildcardsTypedByUsersAreEscaped() {
        assertThat(EmployeeSpecifications.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
    }

    private static EmployeeRequest request(String firstName, String email, String country, Long salary,
                                           Long version, String reason) {
        return new EmployeeRequest(firstName, "Rao", email, "Software Engineer", "Engineering", country, salary,
                LocalDate.of(2022, 1, 1), version, reason);
    }

    private static Employee withId(Employee employee, long id) {
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }
}
