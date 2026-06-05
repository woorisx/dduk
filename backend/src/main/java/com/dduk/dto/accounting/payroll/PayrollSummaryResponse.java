package com.dduk.dto.accounting.payroll;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PayrollSummaryResponse {
    private long activeLedgerCount;
    private long calculatedCount;
    private BigDecimal scheduledPaymentAmount;
    private BigDecimal totalDeductionAmount;
    private BigDecimal totalNetAmount;
    private long unsettledCount;
}
