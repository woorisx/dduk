package com.dduk.dto.accounting.report.analytics;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AccountingReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private AccountingReportBasis reportBasis;
    private AccountingReportType reportType;
    private LocalDateTime generatedAt;
    private FinancialSummaryResponse financialSummary;
    private List<MonthlyTrendResponse> monthlyTrends;
    private List<BalanceCompositionResponse> balanceComposition;
    private List<SalesAnalysisRow> salesAnalysis;
    private List<ExpenseAnalysisRow> expenseAnalysis;
    private List<AccountAnalysisRow> accountAnalysis;
    private List<VoucherFlowRow> voucherFlows;
    private PayrollAnalysisResponse payrollAnalysis;
}
