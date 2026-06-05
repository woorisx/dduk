package com.dduk.dto.accounting.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PayrollDashboardSummary {
    private String targetYearMonth;
    private BigDecimal monthlyPayrollAmount;
    private long calculatedCount;
    private long unsettledCount;
    private BigDecimal unpaidPayrollAmount;
    private LocalDate nextPaymentDate;
    private boolean calculationCompleted;
}
