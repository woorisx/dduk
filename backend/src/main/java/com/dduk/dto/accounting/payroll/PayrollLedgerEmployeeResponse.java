package com.dduk.dto.accounting.payroll;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PayrollLedgerEmployeeResponse {
    private Long id;
    private Long employeeId;
    private String employeeNo;
    private String employeeName;
    private String department;
    private String position;
    private String bankAccount;
    private String paymentStatus;
    private BigDecimal grossAmount;
    private BigDecimal deductionAmount;
    private BigDecimal netAmount;
    private List<PayrollItemResponse> payItems;
    private List<PayrollDeductionResponse> deductionItems;
}
