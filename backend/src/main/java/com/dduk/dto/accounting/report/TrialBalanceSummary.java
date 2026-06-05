package com.dduk.dto.accounting.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TrialBalanceSummary {
    private BigDecimal openingDebitTotal;
    private BigDecimal openingCreditTotal;
    private BigDecimal periodDebitTotal;
    private BigDecimal periodCreditTotal;
    private BigDecimal closingDebitTotal;
    private BigDecimal closingCreditTotal;
    private boolean periodBalanced;
    private boolean closingBalanced;
    private String balanceStatus;
}
