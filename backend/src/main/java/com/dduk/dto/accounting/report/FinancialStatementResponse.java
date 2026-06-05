package com.dduk.dto.accounting.report;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class FinancialStatementResponse {
    private FinancialStatementType statementType;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<FinancialStatementSection> sections;
}
