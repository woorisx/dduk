package com.dduk.controller.accounting.report;

import com.dduk.dto.accounting.report.analytics.AccountingReportBasis;
import com.dduk.dto.accounting.report.analytics.AccountingReportType;
import com.dduk.service.accounting.report.AccountingReportAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting/reports/analytics")
@RequiredArgsConstructor
public class AccountingReportAnalyticsController {

    private final AccountingReportAnalyticsService accountingReportAnalyticsService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "MONTHLY") AccountingReportBasis reportBasis,
            @RequestParam(defaultValue = "COMPREHENSIVE") AccountingReportType reportType
    ) {
        return success(accountingReportAnalyticsService.getReport(startDate, endDate, reportBasis, reportType), "Accounting report analytics loaded.");
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKpis(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return success(accountingReportAnalyticsService.getKpiSummary(startDate, endDate), "Accounting report KPIs loaded.");
    }

    @GetMapping("/trends/profit-loss")
    public ResponseEntity<Map<String, Object>> getProfitLossTrends(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return success(accountingReportAnalyticsService.getMonthlyTrends(startDate, endDate), "Profit and loss trends loaded.");
    }

    @GetMapping("/balance-composition")
    public ResponseEntity<Map<String, Object>> getBalanceComposition(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return success(accountingReportAnalyticsService.getBalanceComposition(startDate, endDate), "Balance composition loaded.");
    }

    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> getAccountAnalysis(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return success(accountingReportAnalyticsService.getAccountAnalysis(startDate, endDate), "Account analysis loaded.");
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "MONTHLY") AccountingReportBasis reportBasis,
            @RequestParam(defaultValue = "COMPREHENSIVE") AccountingReportType reportType
    ) {
        byte[] content = accountingReportAnalyticsService.exportExcel(startDate, endDate, reportBasis, reportType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=accounting-report.csv")
                .contentType(new MediaType("text", "csv"))
                .body(content);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "MONTHLY") AccountingReportBasis reportBasis,
            @RequestParam(defaultValue = "COMPREHENSIVE") AccountingReportType reportType
    ) {
        byte[] content = accountingReportAnalyticsService.exportPdf(startDate, endDate, reportBasis, reportType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=accounting-report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleReportAnalyticsException(RuntimeException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "ACCOUNTING_REPORT_ANALYTICS_REQUEST_INVALID");
        return ResponseEntity.badRequest().body(response);
    }

    private ResponseEntity<Map<String, Object>> success(Object data, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }
}
