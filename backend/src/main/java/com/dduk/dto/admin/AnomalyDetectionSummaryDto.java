package com.dduk.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnomalyDetectionSummaryDto {
    private long activeCount;
    private long openCount;
    private long confirmedCount;
    private long criticalCount;
    private long highCount;
    private String latestDetectedAt;
}
