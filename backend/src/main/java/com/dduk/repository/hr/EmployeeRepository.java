package com.dduk.repository.hr;

import com.dduk.entity.hr.Employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmployeeNo(String employeeNo);
    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByMemberId(Long memberId);

    @Query("""
            SELECT e
            FROM Employee e
            WHERE (:keyword IS NULL OR lower(e.employeeNo) LIKE lower(concat('%', :keyword, '%'))
                OR lower(e.name) LIKE lower(concat('%', :keyword, '%'))
                OR lower(e.department) LIKE lower(concat('%', :keyword, '%'))
                OR lower(e.position) LIKE lower(concat('%', :keyword, '%')))
            ORDER BY e.employeeNo ASC
            """)
    List<Employee> searchForPayroll(@Param("keyword") String keyword);
}
