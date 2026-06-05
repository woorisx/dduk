package com.dduk.controller.accounting.report;

import com.dduk.dto.accounting.report.*;
import com.dduk.service.accounting.report.TrialBalanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting/reports")
@RequiredArgsConstructor
public class TrialBalanceReportController {

    private final TrialBalanceReportService trialBalanceReportService;

    @GetMapping("/trial-balance")
    public ResponseEntity<Map<String, Object>> getTrialBalance(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth,
            @RequestParam(defaultValue = "MONTHLY") ReportBasis reportBasis,
            @RequestParam(defaultValue = "ALL") AccountLevelFilter accountLevel,
            @RequestParam(defaultValue = "false") boolean includeZeroBalance,
            @RequestParam(defaultValue = "true") boolean includeSubAccounts,
            @RequestParam(defaultValue = "false") boolean summaryOnly,
            @RequestParam(defaultValue = "false") boolean profitLossFormat
    ) {
        return success(trialBalanceReportService.getTrialBalance(condition(startDate, endDate, fiscalYear, fiscalMonth, reportBasis, accountLevel,
                includeZeroBalance, includeSubAccounts, summaryOnly, profitLossFormat)), "Trial balance loaded.");
    }

    @GetMapping("/trial-balance/aggregation")
    public ResponseEntity<Map<String, Object>> getAggregation(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(defaultValue = "MONTHLY") ReportBasis reportBasis
    ) {
        return success(trialBalanceReportService.getTrialBalance(condition(startDate, endDate, null, null, reportBasis, AccountLevelFilter.ACCOUNT,
                false, true, false, false)).getSummary(), "Trial balance aggregation loaded.");
    }

    @GetMapping("/accounts/tree")
    public ResponseEntity<Map<String, Object>> getAccountTree() {
        return success(trialBalanceReportService.getAccountTree(), "Account tree loaded.");
    }

    @GetMapping("/trial-balance/export")
    public ResponseEntity<byte[]> exportTrialBalance(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth,
            @RequestParam(defaultValue = "MONTHLY") ReportBasis reportBasis,
            @RequestParam(defaultValue = "ALL") AccountLevelFilter accountLevel,
            @RequestParam(defaultValue = "false") boolean includeZeroBalance,
            @RequestParam(defaultValue = "true") boolean includeSubAccounts,
            @RequestParam(defaultValue = "false") boolean summaryOnly,
            @RequestParam(defaultValue = "false") boolean profitLossFormat
    ) {
        byte[] content = trialBalanceReportService.exportTrialBalanceCsv(condition(startDate, endDate, fiscalYear, fiscalMonth, reportBasis, accountLevel,
                includeZeroBalance, includeSubAccounts, summaryOnly, profitLossFormat));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trial-balance.csv")
                .contentType(new MediaType("text", "csv"))
                .body(content);
    }

    @GetMapping("/financial-statements/{statementType}")
    public ResponseEntity<Map<String, Object>> convertToFinancialStatement(
            @PathVariable FinancialStatementType statementType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return success(trialBalanceReportService.convertToFinancialStatement(statementType,
                condition(startDate, endDate, null, null, ReportBasis.MONTHLY, AccountLevelFilter.ALL, false, true, false,
                        statementType == FinancialStatementType.PROFIT_LOSS)), "Financial statement converted.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleReportException(RuntimeException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "ACCOUNTING_REPORT_REQUEST_INVALID");
        return ResponseEntity.badRequest().body(response);
    }

    private TrialBalanceSearchCondition condition(LocalDate startDate, LocalDate endDate, Integer fiscalYear, Integer fiscalMonth, ReportBasis reportBasis,
                                                  AccountLevelFilter accountLevel, boolean includeZeroBalance,
                                                  boolean includeSubAccounts, boolean summaryOnly, boolean profitLossFormat) {
        TrialBalanceSearchCondition condition = new TrialBalanceSearchCondition();
        if ((startDate == null || endDate == null) && fiscalYear != null && fiscalMonth != null) {
            java.time.YearMonth yearMonth = java.time.YearMonth.of(fiscalYear, fiscalMonth);
            condition.setStartDate(yearMonth.atDay(1));
            condition.setEndDate(yearMonth.atEndOfMonth());
        } else {
            condition.setStartDate(startDate);
            condition.setEndDate(endDate);
        }
        condition.setReportBasis(reportBasis);
        condition.setAccountLevel(accountLevel);
        condition.setIncludeZeroBalance(includeZeroBalance);
        condition.setIncludeSubAccounts(includeSubAccounts);
        condition.setSummaryOnly(summaryOnly);
        condition.setProfitLossFormat(profitLossFormat);
        return condition;
    }

    private ResponseEntity<Map<String, Object>> success(Object data, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }
}
