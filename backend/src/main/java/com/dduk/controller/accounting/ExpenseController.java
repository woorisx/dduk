package com.dduk.controller.accounting;

import com.dduk.config.PrincipalDetails;
import com.dduk.dto.accounting.ExpenseCreateRequest;
import com.dduk.dto.accounting.ExpenseStatusUpdateRequest;
import com.dduk.dto.accounting.ExpenseUpdateRequest;
import com.dduk.service.accounting.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping("/current-employee")
    public ResponseEntity<Map<String, Object>> getCurrentEmployee(
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        return success(expenseService.getCurrentEmployee(resolveMemberId(principalDetails)), "Current employee loaded.");
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getExpenses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size
    ) {
        return success(expenseService.getExpensesPaged(status, employeeId, startDate, endDate, keyword, page, size), "Expense list loaded.");
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword
    ) {
        return success(expenseService.getSummary(status, employeeId, startDate, endDate, keyword), "Expense summary loaded.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getExpense(@PathVariable Long id) {
        return success(expenseService.getExpense(id), "Expense loaded.");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createExpense(
            @RequestBody ExpenseCreateRequest request,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        applyCurrentEmployeeIfMissing(request, principalDetails);
        return success(expenseService.createExpense(request), "Expense saved.");
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateExpense(
            @PathVariable Long id,
            @RequestBody ExpenseUpdateRequest request,
            @AuthenticationPrincipal PrincipalDetails principalDetails
    ) {
        applyCurrentEmployeeIfMissing(request, principalDetails);
        return success(expenseService.updateExpense(id, request), "Expense updated.");
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestBody ExpenseStatusUpdateRequest request
    ) {
        return success(expenseService.updateStatus(id, request), "Expense status updated.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return success(null, "Expense deleted.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleExpenseException(RuntimeException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "EXPENSE_REQUEST_INVALID");
        return ResponseEntity.badRequest().body(response);
    }

    private ResponseEntity<Map<String, Object>> success(Object data, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    private void applyCurrentEmployeeIfMissing(ExpenseCreateRequest request, PrincipalDetails principalDetails) {
        if (request != null) {
            request.setEmployeeId(resolveRequiredCurrentEmployeeId(principalDetails));
        }
    }

    private void applyCurrentEmployeeIfMissing(ExpenseUpdateRequest request, PrincipalDetails principalDetails) {
        if (request != null) {
            request.setEmployeeId(resolveRequiredCurrentEmployeeId(principalDetails));
        }
    }

    private Long resolveMemberId(PrincipalDetails principalDetails) {
        return principalDetails != null ? principalDetails.getMember().getId() : null;
    }

    private Long resolveRequiredCurrentEmployeeId(PrincipalDetails principalDetails) {
        Long employeeId = expenseService.getCurrentEmployeeIdOrNull(resolveMemberId(principalDetails));
        if (employeeId == null) {
            throw new IllegalStateException("Current login account is not linked to an employee.");
        }
        return employeeId;
    }
}
