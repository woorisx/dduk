package com.dduk.dto.accounting.report;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class TrialBalanceResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private ReportBasis reportBasis;
    private AccountLevelFilter accountLevel;
    private boolean includeZeroBalance;
    private boolean includeSubAccounts;
    private boolean summaryOnly;
    private boolean profitLossFormat;
    private TrialBalanceSummary summary;
    private List<TrialBalanceRow> rows;
}
