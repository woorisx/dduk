package com.dduk.dto.accounting.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class TrialBalanceDashboardSummary {
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean balanced;
    private List<TrialBalanceMajorAccountResponse> majorAccounts;
}
