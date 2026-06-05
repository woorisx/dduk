package com.dduk.repository.accounting.payroll;

import com.dduk.entity.accounting.payroll.PayrollLedger;
import com.dduk.entity.accounting.payroll.PayrollStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollLedgerRepository extends JpaRepository<PayrollLedger, Long> {

    List<PayrollLedger> findTop100ByOrderByPaymentDateDescIdDesc();

    List<PayrollLedger> findTop10ByOrderByUpdatedAtDesc();

    long countByStatus(PayrollStatus status);

    @Query("""
            select count(l)
            from PayrollLedger l
            where l.paymentYearMonth = :yearMonth
              and l.status not in :closedStatuses
            """)
    long countOpenLedgersByPaymentYearMonth(
            @Param("yearMonth") String yearMonth,
            @Param("closedStatuses") List<PayrollStatus> closedStatuses
    );

    @Query("select coalesce(sum(l.netAmount), 0) from PayrollLedger l where l.status in :statuses")
    BigDecimal sumNetAmountByStatusIn(@Param("statuses") List<PayrollStatus> statuses);

    @Query("select coalesce(sum(l.netAmount), 0) from PayrollLedger l where l.paymentYearMonth = :yearMonth")
    BigDecimal sumNetAmountByPaymentYearMonth(@Param("yearMonth") String yearMonth);

    @Query("select coalesce(sum(l.netAmount), 0) from PayrollLedger l where l.paymentYearMonth = :yearMonth and l.status in :statuses")
    BigDecimal sumNetAmountByPaymentYearMonthAndStatusIn(
            @Param("yearMonth") String yearMonth,
            @Param("statuses") List<PayrollStatus> statuses
    );

    long countByPaymentYearMonthAndStatus(String paymentYearMonth, PayrollStatus status);

    long countByPaymentYearMonthAndStatusIn(String paymentYearMonth, List<PayrollStatus> statuses);

    Optional<PayrollLedger> findFirstByPaymentYearMonthOrderByPaymentDateAscIdAsc(String paymentYearMonth);

    @Query("""
            select coalesce(min(l.paymentDate), null)
            from PayrollLedger l
            where l.paymentYearMonth = :yearMonth
              and l.status in :statuses
              and l.paymentDate >= :fromDate
            """)
    Optional<LocalDate> findNextPaymentDate(
            @Param("yearMonth") String yearMonth,
            @Param("statuses") List<PayrollStatus> statuses,
            @Param("fromDate") LocalDate fromDate
    );

    @Query("""
            select coalesce(e.departmentSnapshot, '미지정'),
                   coalesce(sum(e.grossAmount), 0),
                   coalesce(sum(e.deductionAmount), 0),
                   coalesce(sum(e.netAmount), 0),
                   count(e)
            from PayrollLedger l
            join l.employees e
            where l.paymentYearMonth between :startYearMonth and :endYearMonth
              and l.status in :statuses
            group by coalesce(e.departmentSnapshot, '미지정')
            order by coalesce(sum(e.grossAmount), 0) desc
            """)
    List<Object[]> aggregateDepartmentPayroll(
            @Param("startYearMonth") String startYearMonth,
            @Param("endYearMonth") String endYearMonth,
            @Param("statuses") List<PayrollStatus> statuses
    );

    @Query("""
            select coalesce(sum(l.grossAmount), 0),
                   coalesce(sum(l.deductionAmount), 0),
                   coalesce(sum(l.netAmount), 0)
            from PayrollLedger l
            where l.paymentYearMonth between :startYearMonth and :endYearMonth
              and l.status in :statuses
            """)
    Object[] sumPayrollAmountsBetweenYearMonth(
            @Param("startYearMonth") String startYearMonth,
            @Param("endYearMonth") String endYearMonth,
            @Param("statuses") List<PayrollStatus> statuses
    );

    @Query("""
            select coalesce(sum(i.amount), 0)
            from PayrollLedger l
            join l.employees e
            join e.payItems i
            where l.paymentYearMonth between :startYearMonth and :endYearMonth
              and l.status in :statuses
              and i.itemType = com.dduk.entity.accounting.payroll.PayrollPayItemType.BONUS
            """)
    BigDecimal sumBonusAmountBetweenYearMonth(
            @Param("startYearMonth") String startYearMonth,
            @Param("endYearMonth") String endYearMonth,
            @Param("statuses") List<PayrollStatus> statuses
    );

    @Query("select coalesce(sum(l.deductionAmount), 0) from PayrollLedger l where l.status in :statuses")
    BigDecimal sumDeductionAmountByStatusIn(@Param("statuses") List<PayrollStatus> statuses);

    @EntityGraph(attributePaths = {
            "employees",
            "employees.employee",
            "journalEntry"
    })
    Optional<PayrollLedger> findWithEmployeesById(Long id);
}
