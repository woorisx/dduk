package com.dduk.dto.accounting;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ExpenseSummaryResponse {

    private long totalCount;
    private BigDecimal totalAmount;
    private long pendingCount;
    private long submittedCount;
    private long approvedCount;
    private long rejectedCount;
}
