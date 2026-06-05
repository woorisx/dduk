package com.dduk.dto.accounting.period;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountingYearCreateRequest {
    private Integer fiscalYear;
    private String createdBy;
}
