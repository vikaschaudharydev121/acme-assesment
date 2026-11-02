package com.acme.salary.salary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {

    /** Ids are monotonic, so id order is insertion order even when two changes share a millisecond. */
    List<SalaryHistory> findByEmployeeIdOrderByIdDesc(Long employeeId);
}
