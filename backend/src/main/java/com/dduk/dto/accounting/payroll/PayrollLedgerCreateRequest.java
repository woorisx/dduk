package com.dduk.dto.accounting.payroll;

import com.dduk.entity.accounting.payroll.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PayrollLedgerCreateRequest {
    private String attributionYearMonth;
    private PayrollType payrollType;
    private PayrollTaxType taxType;
    private PayrollSettlementCycle settlementCycle;
    private PayrollTargetPeriodMode targetPeriodMode;
    private LocalDate paymentDate;
    private String paymentYearMonth;
    private String ledgerName;
    private PayrollSelectionMode settlementItemSelectionMode;
    private List<PayrollSettlementItemType> settlementItems = new ArrayList<>();
    private PayrollSelectionMode employeeSelectionMode;
    private List<Long> employeeIds = new ArrayList<>();
    private String bonusRateOrAmount;
    private String createdBy;
}
