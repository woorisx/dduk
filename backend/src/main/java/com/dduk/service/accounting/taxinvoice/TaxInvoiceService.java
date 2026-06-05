package com.dduk.service.accounting.taxinvoice;

import com.dduk.dto.accounting.taxinvoice.TaxInvoiceCreateRequest;
import com.dduk.dto.accounting.taxinvoice.TaxInvoiceLineRequest;
import com.dduk.dto.accounting.taxinvoice.TaxInvoiceResponse;
import com.dduk.dto.accounting.taxinvoice.TaxInvoiceStatusUpdateRequest;
import com.dduk.entity.accounting.taxinvoice.TaxInvoice;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceLine;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceStatus;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceType;
import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.repository.accounting.taxinvoice.TaxInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class TaxInvoiceService {

    private static final Set<TaxInvoiceStatus> TERMINAL_STATUSES = Set.of(
            TaxInvoiceStatus.CANCELLED,
            TaxInvoiceStatus.AMENDED
    );

    private final TaxInvoiceRepository taxInvoiceRepository;

    @Transactional
    public TaxInvoiceResponse create(TaxInvoiceCreateRequest request) {
        validateCreateRequest(request);

        Totals totals = calculateTotals(request.getLines());
        TaxInvoice taxInvoice = TaxInvoice.builder()
                .taxInvoiceNo(createTaxInvoiceNo(request.getIssueDate()))
                .taxInvoiceType(request.getTaxInvoiceType())
                .vatType(request.getVatType() != null ? request.getVatType() : VatType.TAX_INVOICE)
                .status(TaxInvoiceStatus.DRAFT)
                .voucherId(request.getVoucherId())
                .issueDate(request.getIssueDate())
                .supplierBusinessNo(normalizeBusinessNo(request.getSupplierBusinessNo(), "supplierBusinessNo"))
                .supplierName(requiredText(request.getSupplierName(), "supplierName"))
                .supplierRepresentativeName(trimToNull(request.getSupplierRepresentativeName()))
                .supplierEmail(trimToNull(request.getSupplierEmail()))
                .recipientBusinessNo(normalizeBusinessNo(request.getRecipientBusinessNo(), "recipientBusinessNo"))
                .recipientName(requiredText(request.getRecipientName(), "recipientName"))
                .recipientRepresentativeName(trimToNull(request.getRecipientRepresentativeName()))
                .recipientEmail(trimToNull(request.getRecipientEmail()))
                .supplyAmount(totals.supplyAmount())
                .vatAmount(totals.vatAmount())
                .totalAmount(totals.totalAmount())
                .memo(trimToNull(request.getMemo()))
                .createdBy("system")
                .build();

        AtomicInteger lineNo = new AtomicInteger(1);
        for (TaxInvoiceLineRequest lineRequest : request.getLines()) {
            Totals lineTotals = calculateLineTotals(lineRequest);
            taxInvoice.addLine(TaxInvoiceLine.builder()
                    .lineNo(lineNo.getAndIncrement())
                    .itemName(requiredText(lineRequest.getItemName(), "line.itemName"))
                    .unit(trimToNull(lineRequest.getUnit()))
                    .quantity(nonNegativeOrNull(lineRequest.getQuantity(), "line.quantity"))
                    .unitPrice(nonNegativeOrNull(lineRequest.getUnitPrice(), "line.unitPrice"))
                    .supplyAmount(lineTotals.supplyAmount())
                    .vatAmount(lineTotals.vatAmount())
                    .totalAmount(lineTotals.totalAmount())
                    .description(trimToNull(lineRequest.getDescription()))
                    .build());
        }

        return TaxInvoiceResponse.from(taxInvoiceRepository.save(taxInvoice));
    }

    @Transactional(readOnly = true)
    public List<TaxInvoiceResponse> getList(
            TaxInvoiceType type,
            TaxInvoiceStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String keyword
    ) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return taxInvoiceRepository.findWithFilters(type, status, startDate, endDate, normalizedKeyword)
                .stream()
                .map(TaxInvoiceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaxInvoiceResponse get(Long id) {
        return TaxInvoiceResponse.from(findById(id));
    }

    @Transactional
    public TaxInvoiceResponse requestIssue(Long id) {
        TaxInvoice taxInvoice = findById(id);
        assertStatus(taxInvoice, TaxInvoiceStatus.DRAFT, TaxInvoiceStatus.SEND_FAILED);
        taxInvoice.requestIssue();
        return TaxInvoiceResponse.from(taxInvoice);
    }

    @Transactional
    public TaxInvoiceResponse markIssued(Long id, TaxInvoiceStatusUpdateRequest request) {
        TaxInvoice taxInvoice = findById(id);
        assertStatus(taxInvoice, TaxInvoiceStatus.ISSUE_REQUESTED);
        taxInvoice.markIssued(requiredText(request.getApprovalNo(), "approvalNo"), trimToNull(request.getExternalStatus()));
        return TaxInvoiceResponse.from(taxInvoice);
    }

    @Transactional
    public TaxInvoiceResponse markSent(Long id, TaxInvoiceStatusUpdateRequest request) {
        TaxInvoice taxInvoice = findById(id);
        assertStatus(taxInvoice, TaxInvoiceStatus.ISSUED, TaxInvoiceStatus.SEND_FAILED);
        taxInvoice.markSent(trimToNull(request.getApprovalNo()), trimToNull(request.getExternalStatus()));
        return TaxInvoiceResponse.from(taxInvoice);
    }

    @Transactional
    public TaxInvoiceResponse markSendFailed(Long id, TaxInvoiceStatusUpdateRequest request) {
        TaxInvoice taxInvoice = findById(id);
        assertStatus(taxInvoice, TaxInvoiceStatus.ISSUE_REQUESTED, TaxInvoiceStatus.ISSUED);
        taxInvoice.markSendFailed(requiredText(request.getReason(), "reason"), trimToNull(request.getExternalStatus()));
        return TaxInvoiceResponse.from(taxInvoice);
    }

    @Transactional
    public TaxInvoiceResponse cancel(Long id, TaxInvoiceStatusUpdateRequest request) {
        TaxInvoice taxInvoice = findById(id);
        if (taxInvoice.getStatus() == TaxInvoiceStatus.SENT) {
            throw new IllegalStateException("Sent tax invoices require amendment instead of cancellation.");
        }
        if (TERMINAL_STATUSES.contains(taxInvoice.getStatus())) {
            throw new IllegalStateException("Tax invoice is already closed: " + taxInvoice.getStatus());
        }
        taxInvoice.cancel(trimToNull(request.getReason()));
        return TaxInvoiceResponse.from(taxInvoice);
    }

    @Transactional
    public TaxInvoiceResponse markAmended(Long id, TaxInvoiceStatusUpdateRequest request) {
        TaxInvoice taxInvoice = findById(id);
        assertStatus(taxInvoice, TaxInvoiceStatus.SENT);
        taxInvoice.markAmended(requiredText(request.getReason(), "reason"));
        return TaxInvoiceResponse.from(taxInvoice);
    }

    private TaxInvoice findById(Long id) {
        return taxInvoiceRepository.findByIdWithLines(id)
                .orElseThrow(() -> new IllegalArgumentException("Tax invoice not found: " + id));
    }

    private void validateCreateRequest(TaxInvoiceCreateRequest request) {
        if (request.getTaxInvoiceType() == null) {
            throw new IllegalArgumentException("taxInvoiceType is required.");
        }
        if (request.getIssueDate() == null) {
            request.setIssueDate(LocalDate.now());
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("At least one tax invoice line is required.");
        }
    }

    private Totals calculateTotals(List<TaxInvoiceLineRequest> lines) {
        BigDecimal supply = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (TaxInvoiceLineRequest line : lines) {
            Totals lineTotals = calculateLineTotals(line);
            supply = supply.add(lineTotals.supplyAmount());
            vat = vat.add(lineTotals.vatAmount());
            total = total.add(lineTotals.totalAmount());
        }
        return new Totals(supply, vat, total);
    }

    private Totals calculateLineTotals(TaxInvoiceLineRequest line) {
        BigDecimal supply = nonNegative(line.getSupplyAmount(), "line.supplyAmount");
        BigDecimal vat = line.getVatAmount() != null ? nonNegative(line.getVatAmount(), "line.vatAmount") : BigDecimal.ZERO;
        BigDecimal total = line.getTotalAmount() != null ? nonNegative(line.getTotalAmount(), "line.totalAmount") : supply.add(vat);
        if (supply.add(vat).compareTo(total) != 0) {
            throw new IllegalArgumentException("line.totalAmount must equal supplyAmount + vatAmount.");
        }
        return new Totals(supply, vat, total);
    }

    private void assertStatus(TaxInvoice taxInvoice, TaxInvoiceStatus... allowedStatuses) {
        for (TaxInvoiceStatus allowedStatus : allowedStatuses) {
            if (taxInvoice.getStatus() == allowedStatus) {
                return;
            }
        }
        throw new IllegalStateException("Invalid tax invoice status transition from " + taxInvoice.getStatus() + ".");
    }

    private String createTaxInvoiceNo(LocalDate date) {
        return "TI" + date.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String normalizeBusinessNo(String value, String fieldName) {
        String normalized = requiredText(value, fieldName).replaceAll("[^0-9]", "");
        if (normalized.length() != 10) {
            throw new IllegalArgumentException(fieldName + " must contain 10 digits.");
        }
        return normalized;
    }

    private String requiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal nonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must be zero or greater.");
        }
        return value;
    }

    private BigDecimal nonNegativeOrNull(BigDecimal value, String fieldName) {
        if (value == null) {
            return null;
        }
        return nonNegative(value, fieldName);
    }

    private record Totals(BigDecimal supplyAmount, BigDecimal vatAmount, BigDecimal totalAmount) {
    }
}
