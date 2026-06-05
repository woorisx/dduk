package com.dduk.dto.accounting.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AccountingDashboardResponse {
    private LocalDateTime generatedAt;
    private DashboardKpiSummary kpiSummary;
    private VoucherDashboardSummary voucherSummary;
    private PeriodDashboardSummary periodSummary;
    private TrialBalanceDashboardSummary trialBalanceSummary;
    private PayrollDashboardSummary payrollSummary;
    private CashFlowSummary cashFlowSummary;
    private List<FinancialTrendResponse> financialTrends;
    private List<AccountCompositionResponse> accountComposition;
    private List<AccountingAlertResponse> alerts;
    private List<RecentAccountingActivityResponse> recentActivities;
}
