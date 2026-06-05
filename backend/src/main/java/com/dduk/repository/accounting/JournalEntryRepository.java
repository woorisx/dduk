package com.dduk.repository.accounting;

import com.dduk.entity.accounting.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    boolean existsBySourceTypeAndSourceId(String sourceType, Long sourceId);

    List<JournalEntry> findByStatus(String status);

    List<JournalEntry> findByFiscalYearAndFiscalMonth(Integer fiscalYear, Integer fiscalMonth);

    List<JournalEntry> findTop10ByOrderByCreatedAtDesc();

    boolean existsByFiscalYearAndFiscalMonthAndStatusIn(Integer fiscalYear, Integer fiscalMonth, List<String> statuses);

    long countByFiscalYearAndFiscalMonthAndStatusIn(Integer fiscalYear, Integer fiscalMonth, List<String> statuses);

    @Query("""
        SELECT count(e)
        FROM JournalEntry e
        WHERE e.fiscalYear = :fiscalYear
          AND e.fiscalMonth = :fiscalMonth
          AND e.status <> 'POSTED'
    """)
    long countUnpostedByFiscalPeriod(@Param("fiscalYear") Integer fiscalYear, @Param("fiscalMonth") Integer fiscalMonth);

    @Query("""
        SELECT coalesce(sum(e.totalDebit), 0), coalesce(sum(e.totalCredit), 0)
        FROM JournalEntry e
        WHERE e.fiscalYear = :fiscalYear
          AND e.fiscalMonth = :fiscalMonth
    """)
    Object[] sumEntryTotalsByFiscalPeriod(@Param("fiscalYear") Integer fiscalYear, @Param("fiscalMonth") Integer fiscalMonth);

    @Query("""
        SELECT count(e)
        FROM JournalEntry e
        WHERE e.fiscalYear = :fiscalYear
          AND e.fiscalMonth = :fiscalMonth
          AND e.totalDebit <> e.totalCredit
    """)
    long countUnbalancedByFiscalPeriod(@Param("fiscalYear") Integer fiscalYear, @Param("fiscalMonth") Integer fiscalMonth);

    @Query("""
        SELECT count(e)
        FROM JournalEntry e
        WHERE e.fiscalYear = :fiscalYear
          AND e.fiscalMonth = :fiscalMonth
          AND e.lines IS EMPTY
    """)
    long countEntriesWithoutLines(@Param("fiscalYear") Integer fiscalYear, @Param("fiscalMonth") Integer fiscalMonth);

    @Query("""
        SELECT count(l)
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.fiscalYear = :fiscalYear
          AND e.fiscalMonth = :fiscalMonth
          AND (l.debitAmount < 0 OR l.creditAmount < 0)
    """)
    long countNegativeLines(@Param("fiscalYear") Integer fiscalYear, @Param("fiscalMonth") Integer fiscalMonth);

    @Query("""
        SELECT count(l)
        FROM JournalEntry e
        JOIN e.lines l
        JOIN l.account a
        WHERE e.fiscalYear = :fiscalYear
          AND e.fiscalMonth = :fiscalMonth
          AND (a.deleted = true OR a.status <> 'ACTIVE' OR a.allowPosting = false OR a.children IS NOT EMPTY)
    """)
    long countInvalidPostingAccountLines(@Param("fiscalYear") Integer fiscalYear, @Param("fiscalMonth") Integer fiscalMonth);

    List<JournalEntry> findByFiscalYearOrderByTransactionDateDesc(Integer fiscalYear);

    @Query("""
        SELECT l.account.code,
               SUM(l.debitAmount),
               SUM(l.creditAmount)
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.status = 'POSTED'
          AND (:fiscalYear IS NULL OR e.fiscalYear = :fiscalYear)
          AND (:fiscalMonth IS NULL OR e.fiscalMonth = :fiscalMonth)
        GROUP BY l.account.code
    """)
    List<Object[]> aggregateByAccountCode(
            @Param("fiscalYear") Integer fiscalYear,
            @Param("fiscalMonth") Integer fiscalMonth
    );

    @Query("""
        SELECT l.account.id,
               coalesce(sum(l.debitAmount), 0),
               coalesce(sum(l.creditAmount), 0)
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.status = 'POSTED'
          AND e.transactionDate < :endExclusive
        GROUP BY l.account.id
    """)
    List<Object[]> aggregatePostedBeforeDate(@Param("endExclusive") java.time.LocalDate endExclusive);

    @Query("""
        SELECT l.account.id,
               coalesce(sum(l.debitAmount), 0),
               coalesce(sum(l.creditAmount), 0)
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.status = 'POSTED'
          AND e.transactionDate between :startDate and :endDate
        GROUP BY l.account.id
    """)
    List<Object[]> aggregatePostedBetweenDates(
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );

    @Query("""
        SELECT l.account.type,
               coalesce(sum(l.debitAmount), 0),
               coalesce(sum(l.creditAmount), 0)
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.status = 'POSTED'
          AND e.transactionDate between :startDate and :endDate
        GROUP BY l.account.type
    """)
    List<Object[]> aggregatePostedByAccountTypeBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT l.account.type,
               coalesce(sum(l.debitAmount), 0),
               coalesce(sum(l.creditAmount), 0)
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.status = 'POSTED'
          AND e.transactionDate <= :endDate
        GROUP BY l.account.type
    """)
    List<Object[]> aggregatePostedByAccountTypeBeforeOrOnDate(
            @Param("endDate") LocalDate endDate
    );


    @Query("""
        SELECT e.fiscalYear,
               e.fiscalMonth,
               l.account.type,
               coalesce(sum(l.debitAmount), 0),
               coalesce(sum(l.creditAmount), 0)
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.status = 'POSTED'
          AND e.transactionDate between :startDate and :endDate
        GROUP BY e.fiscalYear, e.fiscalMonth, l.account.type
        ORDER BY e.fiscalYear ASC, e.fiscalMonth ASC
    """)
    List<Object[]> aggregatePostedMonthlyByAccountType(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT coalesce(sum(l.debitAmount), 0),
               coalesce(sum(l.creditAmount), 0)
        FROM JournalEntry e
        JOIN e.lines l
        JOIN l.account a
        WHERE e.status = 'POSTED'
          AND e.transactionDate between :startDate and :endDate
          AND a.type = com.dduk.entity.accounting.AccountType.ASSET
          AND (a.code LIKE '111%' OR a.code IN ('1001', '1002') OR a.name LIKE '%현금%' OR a.name LIKE '%예금%')
    """)
    Object[] aggregateCashFlowBetweenDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT e.transactionDate, e.journalNo, e.description,
               l.debitAmount, l.creditAmount, l.description
        FROM JournalEntry e
        JOIN e.lines l
        WHERE e.status = 'POSTED'
          AND l.account.code = :accountCode
          AND (:fiscalYear IS NULL OR e.fiscalYear = :fiscalYear)
          AND (:fiscalMonth IS NULL OR e.fiscalMonth = :fiscalMonth)
        ORDER BY e.transactionDate ASC, e.id ASC
    """)
    List<Object[]> findGeneralLedgerByAccount(
            @Param("accountCode") String accountCode,
            @Param("fiscalYear") Integer fiscalYear,
            @Param("fiscalMonth") Integer fiscalMonth
    );
}
