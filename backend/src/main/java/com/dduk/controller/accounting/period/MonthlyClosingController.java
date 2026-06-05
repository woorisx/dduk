package com.dduk.controller.accounting.period;

import com.dduk.dto.accounting.period.*;
import com.dduk.service.accounting.period.MonthlyClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting/monthly-closing")
@RequiredArgsConstructor
public class MonthlyClosingController {

    private final MonthlyClosingService monthlyClosingService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) Integer fiscalYear,
            @RequestParam(required = false) Integer fiscalMonth
    ) {
        return success(monthlyClosingService.getSummary(fiscalYear, fiscalMonth), "Monthly closing summary loaded.");
    }

    @GetMapping("/periods")
    public ResponseEntity<Map<String, Object>> getPeriods(@RequestParam(required = false) Integer fiscalYear) {
        return success(monthlyClosingService.getPeriods(fiscalYear), "Accounting periods loaded.");
    }

    @GetMapping("/periods/{fiscalYear}/{fiscalMonth}")
    public ResponseEntity<Map<String, Object>> getPeriodStatus(@PathVariable Integer fiscalYear, @PathVariable Integer fiscalMonth) {
        return success(monthlyClosingService.getStatus(fiscalYear, fiscalMonth), "Accounting period status loaded.");
    }

    @PostMapping("/periods")
    public ResponseEntity<Map<String, Object>> createPeriod(@RequestBody AccountingPeriodCreateRequest request) {
        return success(monthlyClosingService.createPeriod(request), "Accounting period created.");
    }

    @PostMapping("/periods/year")
    public ResponseEntity<Map<String, Object>> createYearPeriods(@RequestBody AccountingYearCreateRequest request) {
        return success(monthlyClosingService.createYearPeriods(request), "Accounting year periods created.");
    }

    @PostMapping("/periods/{fiscalYear}/{fiscalMonth}/validate")
    public ResponseEntity<Map<String, Object>> validatePeriod(
            @PathVariable Integer fiscalYear,
            @PathVariable Integer fiscalMonth,
            @RequestBody(required = false) ClosingActionRequest request
    ) {
        return success(monthlyClosingService.validatePeriod(fiscalYear, fiscalMonth, request), "Closing validation completed.");
    }

    @PostMapping("/periods/{fiscalYear}/{fiscalMonth}/close")
    public ResponseEntity<Map<String, Object>> closePeriod(
            @PathVariable Integer fiscalYear,
            @PathVariable Integer fiscalMonth,
            @RequestBody(required = false) ClosingActionRequest request
    ) {
        return success(monthlyClosingService.closePeriod(fiscalYear, fiscalMonth, request), "Monthly closing completed.");
    }

    @PostMapping("/periods/{fiscalYear}/{fiscalMonth}/reopen")
    public ResponseEntity<Map<String, Object>> reopenPeriod(
            @PathVariable Integer fiscalYear,
            @PathVariable Integer fiscalMonth,
            @RequestBody(required = false) ClosingActionRequest request
    ) {
        return success(monthlyClosingService.reopenPeriod(fiscalYear, fiscalMonth, request), "Accounting period reopened.");
    }

    @GetMapping("/periods/{periodId}/logs")
    public ResponseEntity<Map<String, Object>> getLogs(@PathVariable Long periodId) {
        return success(monthlyClosingService.getLogs(periodId), "Closing logs loaded.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleClosingException(RuntimeException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "MONTHLY_CLOSING_REQUEST_INVALID");
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
