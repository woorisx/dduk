package com.dduk.controller.accounting.voucher;

import com.dduk.dto.accounting.voucher.VoucherRequest;
import com.dduk.dto.accounting.voucher.VoucherDetailResponse;
import com.dduk.entity.accounting.AccountType;
import com.dduk.entity.accounting.voucher.enums.VoucherStatus;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import com.dduk.service.accounting.voucher.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getVouchers(
            @RequestParam(required = false) VoucherType type,
            @RequestParam(required = false) VoucherStatus status,
            @RequestParam(required = false) java.time.LocalDate startDate,
            @RequestParam(required = false) java.time.LocalDate endDate,
            @RequestParam(required = false) String keyword
    ) {
        return success(voucherService.getVouchers(type, status, startDate, endDate, keyword), "Voucher list loaded.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getVoucherDetail(@PathVariable Long id) {
        return success(voucherService.getVoucherDetail(id), "Voucher detail loaded.");
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return success(voucherService.getSummary(), "Voucher summary loaded.");
    }

    @GetMapping("/accounts/search")
    public ResponseEntity<Map<String, Object>> searchAccounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AccountType type,
            @RequestParam(defaultValue = "false") boolean cashOnly
    ) {
        return success(voucherService.searchAccounts(keyword, type, cashOnly), "Account search completed.");
    }

    @GetMapping("/accounts/tree-search")
    public ResponseEntity<Map<String, Object>> searchAccountTree(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AccountType type,
            @RequestParam(defaultValue = "false") boolean cashOnly
    ) {
        return success(voucherService.searchAccounts(keyword, type, cashOnly), "Account tree search completed.");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createVoucher(@RequestBody VoucherRequest request) {
        return success(voucherService.createVoucher(request), "Voucher saved.");
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam VoucherStatus status) {
        return success(voucherService.updateStatus(id, status), "Voucher status updated.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleVoucherException(RuntimeException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "VOUCHER_REQUEST_INVALID");
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
