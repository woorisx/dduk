package com.dduk.dto.accounting.period;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AccountingPeriodCreateRequest {
    private Integer fiscalYear;
    private Integer fiscalMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private String createdBy;
}
