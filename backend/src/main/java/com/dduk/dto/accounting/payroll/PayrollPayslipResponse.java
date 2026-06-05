package com.dduk.dto.accounting.payroll;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class PayrollPayslipResponse {
    private Long ledgerId;
    private String ledgerName;
    private String attributionYearMonth;
    private LocalDate paymentDate;
    private String employeeNo;
    private String employeeName;
    private String department;
    private String position;
    private BigDecimal grossAmount;
    private BigDecimal deductionAmount;
    private BigDecimal netAmount;
    private List<PayrollItemResponse> payItems;
    private List<PayrollDeductionResponse> deductionItems;
}
