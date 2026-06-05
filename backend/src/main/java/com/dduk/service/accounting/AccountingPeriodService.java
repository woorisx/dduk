package com.dduk.service.accounting;

import com.dduk.entity.accounting.period.AccountingPeriod;
import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.repository.accounting.period.AccountingPeriodRepository;
import com.dduk.repository.accounting.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountingPeriodService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final JournalEntryRepository journalEntryRepository;

    @Transactional(readOnly = true)
    public List<AccountingPeriod> getAllPeriods() {
        return accountingPeriodRepository.findAll();
    }

    @Transactional(readOnly = true)
    public boolean isClosed(int fiscalYear, int fiscalMonth) {
        return accountingPeriodRepository
                .existsByFiscalYearAndFiscalMonthAndStatus(fiscalYear, fiscalMonth, AccountingPeriodStatus.CLOSED);
    }

    @Transactional
    public AccountingPeriod closePeriod(int fiscalYear, int fiscalMonth, String closedBy) {
        boolean hasPending = journalEntryRepository.existsByFiscalYearAndFiscalMonthAndStatusIn(
                fiscalYear, fiscalMonth, List.of("DRAFT"));
        if (hasPending) {
            throw new IllegalStateException(
                    String.format("%d-%02d 기간에 기표되지 않은 전표(DRAFT)가 존재하여 마감할 수 없습니다.", fiscalYear, fiscalMonth));
        }

        AccountingPeriod period = accountingPeriodRepository
                .findByFiscalYearAndFiscalMonth(fiscalYear, fiscalMonth)
                .orElseGet(() -> AccountingPeriod.builder()
                        .fiscalYear(fiscalYear)
                        .fiscalMonth(fiscalMonth)
                        .startDate(java.time.LocalDate.of(fiscalYear, fiscalMonth, 1))
                        .endDate(java.time.LocalDate.of(fiscalYear, fiscalMonth, 1).withDayOfMonth(java.time.LocalDate.of(fiscalYear, fiscalMonth, 1).lengthOfMonth()))
                        .status(AccountingPeriodStatus.OPEN)
                        .build());

        period.close(closedBy);
        return accountingPeriodRepository.save(period);
    }

    @Transactional
    public AccountingPeriod reopenPeriod(int fiscalYear, int fiscalMonth, String reopenedBy) {
        AccountingPeriod period = accountingPeriodRepository
                .findByFiscalYearAndFiscalMonth(fiscalYear, fiscalMonth)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 회계 기간입니다: " + fiscalYear + "-" + String.format("%02d", fiscalMonth)));

        period.reopen(reopenedBy);
        return accountingPeriodRepository.save(period);
    }
}
