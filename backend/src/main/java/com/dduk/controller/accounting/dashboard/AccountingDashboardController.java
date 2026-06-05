package com.dduk.controller.accounting.dashboard;

import com.dduk.service.accounting.dashboard.AccountingDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting/dashboard")
@RequiredArgsConstructor
public class AccountingDashboardController {

    private final AccountingDashboardService accountingDashboardService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth
    ) {
        return success(accountingDashboardService.getDashboard(fiscalYear, fiscalMonth), "Accounting dashboard loaded.");
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKpis(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth
    ) {
        return success(accountingDashboardService.getKpiSummary(fiscalYear, fiscalMonth), "Accounting dashboard KPIs loaded.");
    }

    @GetMapping("/trends/profit-loss")
    public ResponseEntity<Map<String, Object>> getProfitLossTrends(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth
    ) {
        return success(accountingDashboardService.getFinancialTrends(fiscalYear, fiscalMonth), "Profit and loss trend loaded.");
    }

    @GetMapping("/vouchers")
    public ResponseEntity<Map<String, Object>> getVoucherSummary(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth
    ) {
        return success(accountingDashboardService.getVoucherSummary(fiscalYear, fiscalMonth), "Voucher dashboard summary loaded.");
    }

    @GetMapping("/period")
    public ResponseEntity<Map<String, Object>> getPeriodSummary(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth
    ) {
        return success(accountingDashboardService.getPeriodSummary(fiscalYear, fiscalMonth), "Accounting period dashboard summary loaded.");
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlerts(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth
    ) {
        return success(accountingDashboardService.getAlerts(fiscalYear, fiscalMonth), "Accounting dashboard alerts loaded.");
    }

    @GetMapping("/activities")
    public ResponseEntity<Map<String, Object>> getActivities() {
        return success(accountingDashboardService.getRecentActivities(), "Recent accounting activities loaded.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleDashboardException(RuntimeException exception) {
        exception.printStackTrace(); // 진단을 위해 스택 트레이스를 콘솔에 출력
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "ACCOUNTING_DASHBOARD_REQUEST_INVALID");
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
