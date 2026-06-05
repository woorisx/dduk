package com.dduk.dto.accounting.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FinancialTrendResponse {
    private String period;
    private BigDecimal revenue;
    private BigDecimal expense;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
}
