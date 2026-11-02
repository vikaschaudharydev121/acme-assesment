package com.acme.salary.insights;

import com.acme.salary.employee.Employee;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Read-only projection repository: the insights module cannot modify employees. */
interface PayRecordRepository extends Repository<Employee, Long> {

    @Query("select new com.acme.salary.insights.PayRecord(e.countryCode, e.department, e.jobTitle, e.annualSalary) "
            + "from Employee e")
    List<PayRecord> findAllPayRecords();

    @Query("select new com.acme.salary.insights.PayRecord(e.countryCode, e.department, e.jobTitle, e.annualSalary) "
            + "from Employee e where e.countryCode = :countryCode")
    List<PayRecord> findPayRecordsByCountry(String countryCode);
}
