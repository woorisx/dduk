package com.dduk.controller.accounting.taxinvoice;

import com.dduk.dto.accounting.taxinvoice.TaxInvoiceCreateRequest;
import com.dduk.dto.accounting.taxinvoice.TaxInvoiceStatusUpdateRequest;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceStatus;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceType;
import com.dduk.service.accounting.taxinvoice.TaxInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting/tax-invoices")
@RequiredArgsConstructor
public class TaxInvoiceController {

    private final TaxInvoiceService taxInvoiceService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTaxInvoices(
            @RequestParam(required = false) TaxInvoiceType type,
            @RequestParam(required = false) TaxInvoiceStatus status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword
    ) {
        return success(taxInvoiceService.getList(type, status, startDate, endDate, keyword), "Tax invoice list loaded.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTaxInvoice(@PathVariable Long id) {
        return success(taxInvoiceService.get(id), "Tax invoice loaded.");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTaxInvoice(@RequestBody TaxInvoiceCreateRequest request) {
        return success(taxInvoiceService.create(request), "Tax invoice draft saved.");
    }

    @PatchMapping("/{id}/request-issue")
    public ResponseEntity<Map<String, Object>> requestIssue(@PathVariable Long id) {
        return success(taxInvoiceService.requestIssue(id), "Tax invoice issue requested.");
    }

    @PatchMapping("/{id}/issued")
    public ResponseEntity<Map<String, Object>> markIssued(
            @PathVariable Long id,
            @RequestBody TaxInvoiceStatusUpdateRequest request
    ) {
        return success(taxInvoiceService.markIssued(id, request), "Tax invoice marked as issued.");
    }

    @PatchMapping("/{id}/sent")
    public ResponseEntity<Map<String, Object>> markSent(
            @PathVariable Long id,
            @RequestBody TaxInvoiceStatusUpdateRequest request
    ) {
        return success(taxInvoiceService.markSent(id, request), "Tax invoice marked as sent.");
    }

    @PatchMapping("/{id}/send-failed")
    public ResponseEntity<Map<String, Object>> markSendFailed(
            @PathVariable Long id,
            @RequestBody TaxInvoiceStatusUpdateRequest request
    ) {
        return success(taxInvoiceService.markSendFailed(id, request), "Tax invoice send failure recorded.");
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable Long id,
            @RequestBody TaxInvoiceStatusUpdateRequest request
    ) {
        return success(taxInvoiceService.cancel(id, request), "Tax invoice cancelled.");
    }

    @PatchMapping("/{id}/amended")
    public ResponseEntity<Map<String, Object>> markAmended(
            @PathVariable Long id,
            @RequestBody TaxInvoiceStatusUpdateRequest request
    ) {
        return success(taxInvoiceService.markAmended(id, request), "Tax invoice marked as amended.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleTaxInvoiceException(RuntimeException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "TAX_INVOICE_REQUEST_INVALID");
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
