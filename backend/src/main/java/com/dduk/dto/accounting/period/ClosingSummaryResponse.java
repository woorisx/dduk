package com.dduk.dto.accounting.period;

import com.dduk.entity.accounting.period.AccountingPeriodStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ClosingSummaryResponse {
    private String currentPeriod;
    private AccountingPeriodStatus status;
    private long unapprovedVoucherCount;
    private long unpostedVoucherCount;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean balanced;
    private String mismatchStatus;
}
