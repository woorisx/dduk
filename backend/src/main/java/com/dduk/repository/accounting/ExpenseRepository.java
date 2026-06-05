package com.dduk.repository.accounting;

import com.dduk.entity.accounting.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByEmployeeId(Long employeeId);

    List<Expense> findByStatus(String status);

    List<Expense> findAllByOrderByIdDesc();

    @Query("""
            SELECT e
            FROM Expense e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:employeeId IS NULL OR e.employeeId = :employeeId)
              AND (:startDate IS NULL OR e.expenseDate >= :startDate)
              AND (:endDate IS NULL OR e.expenseDate <= :endDate)
              AND (:keyword IS NULL
                   OR lower(e.category) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(e.description) LIKE lower(concat('%', :keyword, '%')))
            ORDER BY e.expenseDate DESC, e.id DESC
            """)
    List<Expense> findWithFilters(
            @Param("status") String status,
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword
    );

    @Query("""
            SELECT e
            FROM Expense e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:employeeId IS NULL OR e.employeeId = :employeeId)
              AND (:startDate IS NULL OR e.expenseDate >= :startDate)
              AND (:endDate IS NULL OR e.expenseDate <= :endDate)
              AND (:keyword IS NULL
                   OR lower(e.category) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(e.description) LIKE lower(concat('%', :keyword, '%')))
            """)
    Page<Expense> findWithFiltersPage(
            @Param("status") String status,
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
