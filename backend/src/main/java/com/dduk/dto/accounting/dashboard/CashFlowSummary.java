package com.dduk.dto.accounting.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CashFlowSummary {
    private BigDecimal cashInflow;
    private BigDecimal cashOutflow;
    private BigDecimal netCashFlow;
    private String basis;
}
