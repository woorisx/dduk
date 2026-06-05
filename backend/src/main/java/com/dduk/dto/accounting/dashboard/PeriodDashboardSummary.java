package com.dduk.dto.accounting.dashboard;

import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import com.dduk.entity.accounting.period.ClosingValidationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PeriodDashboardSummary {
    private String currentPeriod;
    private Integer fiscalYear;
    private Integer fiscalMonth;
    private AccountingPeriodStatus status;
    private ClosingValidationStatus validationStatus;
    private long unpostedVoucherCount;
    private boolean balanced;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean periodFound;
}
