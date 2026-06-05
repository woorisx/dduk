package com.dduk.dto.accounting.period;

import com.dduk.entity.accounting.period.AccountingPeriod;
import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.entity.accounting.period.ClosingValidationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AccountingPeriodResponse {
    private Long id;
    private String periodKey;
    private Integer fiscalYear;
    private Integer fiscalMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private AccountingPeriodStatus status;
    private long voucherCount;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private ClosingValidationStatus validationStatus;
    private LocalDateTime closedAt;
    private String closedBy;
    private boolean reopened;

    public static AccountingPeriodResponse of(AccountingPeriod period, PeriodAggregation aggregation, ClosingValidationStatus validationStatus) {
        return AccountingPeriodResponse.builder()
                .id(period.getId())
                .periodKey(period.getPeriodKey())
                .fiscalYear(period.getFiscalYear())
                .fiscalMonth(period.getFiscalMonth())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus())
                .voucherCount(aggregation.voucherCount())
                .totalDebit(aggregation.totalDebit())
                .totalCredit(aggregation.totalCredit())
                .validationStatus(validationStatus)
                .closedAt(period.getClosedAt())
                .closedBy(period.getClosedBy())
                .reopened(period.getReopenCount() != null && period.getReopenCount() > 0)
                .build();
    }
}
