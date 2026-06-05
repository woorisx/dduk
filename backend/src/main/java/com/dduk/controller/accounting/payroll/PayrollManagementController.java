package com.dduk.controller.accounting.payroll;

import com.dduk.dto.accounting.payroll.*;
import com.dduk.service.accounting.payroll.PayrollManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounting/payroll-ledgers")
@RequiredArgsConstructor
public class PayrollManagementController {

    private final PayrollManagementService payrollManagementService;

    @GetMapping("/summary")
    public PayrollSummaryResponse getSummary() {
        return payrollManagementService.getSummary();
    }

    @GetMapping
    public List<PayrollLedgerResponse> getLedgers() {
        return payrollManagementService.getLedgers();
    }

    @PostMapping
    public PayrollLedgerResponse createLedger(@RequestBody PayrollLedgerCreateRequest request) {
        return payrollManagementService.createLedger(request, false);
    }

    @PostMapping("/calculate")
    public PayrollLedgerResponse createAndCalculate(@RequestBody PayrollLedgerCreateRequest request) {
        return payrollManagementService.createLedger(request, true);
    }

    @GetMapping("/{ledgerId}")
    public PayrollLedgerResponse getLedger(@PathVariable Long ledgerId) {
        return payrollManagementService.getLedger(ledgerId);
    }

    @PostMapping("/{ledgerId}/calculate")
    public PayrollLedgerResponse calculateLedger(@PathVariable Long ledgerId) {
        return payrollManagementService.calculateLedger(ledgerId);
    }

    @PostMapping("/{ledgerId}/confirm")
    public PayrollLedgerResponse confirmLedger(@PathVariable Long ledgerId) {
        return payrollManagementService.confirmLedger(ledgerId);
    }

    @GetMapping("/{ledgerId}/payslips")
    public List<PayrollPayslipResponse> getPayslips(@PathVariable Long ledgerId) {
        return payrollManagementService.getPayslips(ledgerId);
    }

    @GetMapping("/employees/search")
    public List<PayrollEmployeeSearchResponse> searchEmployees(@RequestParam(required = false) String keyword) {
        return payrollManagementService.searchEmployees(keyword);
    }
}
