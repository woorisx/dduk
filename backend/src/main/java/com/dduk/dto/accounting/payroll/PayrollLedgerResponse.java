package com.dduk.dto.accounting.payroll;

import com.dduk.entity.accounting.payroll.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PayrollLedgerResponse {
    private Long id;
    private String attributionYearMonth;
    private PayrollType payrollType;
    private String payrollTypeLabel;
    private PayrollTaxType taxType;
    private PayrollSettlementCycle settlementCycle;
    private PayrollTargetPeriodMode targetPeriodMode;
    private LocalDate paymentDate;
    private String paymentYearMonth;
    private String ledgerName;
    private PayrollStatus status;
    private Integer headCount;
    private BigDecimal grossAmount;
    private BigDecimal deductionAmount;
    private BigDecimal netAmount;
    private String bonusRateOrAmount;
    private Boolean preEmployeeChecked;
    private Boolean preInsuranceCalculated;
    private Boolean preSettlementValidated;
    private Boolean preAccountValidated;
    private String createdBy;
    private LocalDateTime createdAt;
    private Long journalEntryId;
    private List<String> settlementItems;
    private List<PayrollLedgerEmployeeResponse> employees;
}
