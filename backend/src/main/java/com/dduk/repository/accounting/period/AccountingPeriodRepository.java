package com.dduk.repository.accounting.period;

import com.dduk.entity.accounting.period.AccountingPeriod;
import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    Optional<AccountingPeriod> findByFiscalYearAndFiscalMonth(Integer fiscalYear, Integer fiscalMonth);

    boolean existsByFiscalYearAndFiscalMonth(Integer fiscalYear, Integer fiscalMonth);

    boolean existsByFiscalYearAndFiscalMonthAndStatus(Integer fiscalYear, Integer fiscalMonth, AccountingPeriodStatus status);

    List<AccountingPeriod> findByFiscalYearOrderByFiscalMonthAsc(Integer fiscalYear);

    List<AccountingPeriod> findAllByOrderByFiscalYearDescFiscalMonthDesc();

    @Query("""
            select case when count(p) > 0 then true else false end
            from AccountingPeriod p
            where p.status in :statuses
              and p.startDate <= :endDate
              and p.endDate >= :startDate
              and (:excludeId is null or p.id <> :excludeId)
            """)
    boolean existsOverlappingLockedPeriod(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<AccountingPeriodStatus> statuses,
            @Param("excludeId") Long excludeId
    );
}
