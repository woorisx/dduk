package com.dduk.dto.accounting.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DashboardKpiSummary {
    private BigDecimal monthlyRevenue;
    private BigDecimal monthlyRevenueChangeRate;
    private BigDecimal monthlyExpense;
    private BigDecimal monthlyExpenseChangeRate;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private long unapprovedVoucherCount;
    private String monthlyClosingStatus;
}
