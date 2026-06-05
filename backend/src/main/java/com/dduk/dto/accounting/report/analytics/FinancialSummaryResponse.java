package com.dduk.dto.accounting.report.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FinancialSummaryResponse {
    private BigDecimal totalRevenue;
    private BigDecimal revenueChangeRate;
    private BigDecimal totalExpense;
    private BigDecimal expenseChangeRate;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal debtRatio;
    private BigDecimal currentRatio;
    private BalanceSheetSummary balanceSheetSummary;
    private ProfitLossResponse profitLossSummary;
}
