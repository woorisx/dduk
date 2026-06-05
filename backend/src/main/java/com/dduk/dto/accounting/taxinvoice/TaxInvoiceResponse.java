package com.dduk.dto.accounting.taxinvoice;

import com.dduk.entity.accounting.taxinvoice.TaxInvoice;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceStatus;
import com.dduk.entity.accounting.taxinvoice.TaxInvoiceType;
import com.dduk.entity.accounting.voucher.enums.VatType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaxInvoiceResponse {
    private Long id;
    private String taxInvoiceNo;
    private TaxInvoiceType taxInvoiceType;
    private VatType vatType;
    private TaxInvoiceStatus status;
    private Long voucherId;
    private LocalDate issueDate;
    private String supplierBusinessNo;
    private String supplierName;
    private String supplierRepresentativeName;
    private String supplierEmail;
    private String recipientBusinessNo;
    private String recipientName;
    private String recipientRepresentativeName;
    private String recipientEmail;
    private BigDecimal supplyAmount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private String externalApprovalNo;
    private String externalStatus;
    private String failureReason;
    private String memo;
    private LocalDateTime issueRequestedAt;
    private LocalDateTime issuedAt;
    private LocalDateTime sentAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime amendedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TaxInvoiceLineResponse> lines;

    public static TaxInvoiceResponse from(TaxInvoice taxInvoice) {
        TaxInvoiceResponse response = new TaxInvoiceResponse();
        response.setId(taxInvoice.getId());
        response.setTaxInvoiceNo(taxInvoice.getTaxInvoiceNo());
        response.setTaxInvoiceType(taxInvoice.getTaxInvoiceType());
        response.setVatType(taxInvoice.getVatType());
        response.setStatus(taxInvoice.getStatus());
        response.setVoucherId(taxInvoice.getVoucherId());
        response.setIssueDate(taxInvoice.getIssueDate());
        response.setSupplierBusinessNo(taxInvoice.getSupplierBusinessNo());
        response.setSupplierName(taxInvoice.getSupplierName());
        response.setSupplierRepresentativeName(taxInvoice.getSupplierRepresentativeName());
        response.setSupplierEmail(taxInvoice.getSupplierEmail());
        response.setRecipientBusinessNo(taxInvoice.getRecipientBusinessNo());
        response.setRecipientName(taxInvoice.getRecipientName());
        response.setRecipientRepresentativeName(taxInvoice.getRecipientRepresentativeName());
        response.setRecipientEmail(taxInvoice.getRecipientEmail());
        response.setSupplyAmount(taxInvoice.getSupplyAmount());
        response.setVatAmount(taxInvoice.getVatAmount());
        response.setTotalAmount(taxInvoice.getTotalAmount());
        response.setExternalApprovalNo(taxInvoice.getExternalApprovalNo());
        response.setExternalStatus(taxInvoice.getExternalStatus());
        response.setFailureReason(taxInvoice.getFailureReason());
        response.setMemo(taxInvoice.getMemo());
        response.setIssueRequestedAt(taxInvoice.getIssueRequestedAt());
        response.setIssuedAt(taxInvoice.getIssuedAt());
        response.setSentAt(taxInvoice.getSentAt());
        response.setCancelledAt(taxInvoice.getCancelledAt());
        response.setAmendedAt(taxInvoice.getAmendedAt());
        response.setCreatedBy(taxInvoice.getCreatedBy());
        response.setCreatedAt(taxInvoice.getCreatedAt());
        response.setUpdatedAt(taxInvoice.getUpdatedAt());
        response.setLines(taxInvoice.getLines().stream().map(TaxInvoiceLineResponse::from).toList());
        return response;
    }
}
