package com.dduk.dto.admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnomalyRefreshResponseDto {
    private long detectedCount;
    private long activatedCount;
    private long deactivatedCount;
    private AnomalyDetectionSummaryDto summary;
}
