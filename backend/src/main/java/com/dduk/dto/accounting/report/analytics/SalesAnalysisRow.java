package com.dduk.dto.accounting.report.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalesAnalysisRow {
    private String vendorName;
    private BigDecimal salesAmount;
    private BigDecimal vatAmount;
    private BigDecimal netSales;
    private BigDecimal changeRate;
}
