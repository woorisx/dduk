package com.dduk.dto.accounting.payroll;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PayrollDeductionResponse {
    private String type;
    private String name;
    private BigDecimal amount;
}
