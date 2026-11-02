package com.acme.salary.salary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SalaryHistoryService {

    public static final String INITIAL_SALARY_REASON = "Initial salary";

    private final SalaryHistoryRepository repository;

    public SalaryHistoryService(SalaryHistoryRepository repository) {
        this.repository = repository;
    }

    /** Must join the caller's transaction so a salary change and its audit row commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInitial(Long employeeId, Money salary) {
        repository.save(new SalaryHistory(employeeId, null, salary, INITIAL_SALARY_REASON));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChange(Long employeeId, Money previous, Money current, String reason) {
        repository.save(new SalaryHistory(employeeId, previous, current, reason));
    }

    @Transactional(readOnly = true)
    public List<SalaryHistoryResponse> historyFor(Long employeeId) {
        return repository.findByEmployeeIdOrderByIdDesc(employeeId).stream()
                .map(SalaryHistoryResponse::from)
                .toList();
    }
}
