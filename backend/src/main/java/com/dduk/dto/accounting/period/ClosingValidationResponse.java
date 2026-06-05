package com.dduk.dto.accounting.period;

import com.dduk.entity.accounting.period.ClosingValidationStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ClosingValidationResponse {
    private String periodKey;
    private ClosingValidationStatus overallStatus;
    private boolean closable;
    private List<ClosingValidationResult> results;
}
