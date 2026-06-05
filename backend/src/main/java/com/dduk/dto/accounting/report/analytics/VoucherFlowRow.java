package com.dduk.dto.accounting.report.analytics;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoucherFlowRow {
    private String period;
    private long totalCount;
    private long approvedCount;
    private long postedCount;
    private long cancelledCount;
}
