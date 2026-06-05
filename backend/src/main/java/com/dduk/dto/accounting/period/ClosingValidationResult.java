package com.dduk.dto.accounting.period;

import com.dduk.entity.accounting.period.ClosingValidationStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClosingValidationResult {
    private String validationType;
    private ClosingValidationStatus status;
    private String detail;
    private long targetCount;
    private boolean actionRequired;
}
