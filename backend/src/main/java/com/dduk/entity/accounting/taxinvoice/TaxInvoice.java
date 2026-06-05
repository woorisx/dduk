package com.dduk.entity.accounting.taxinvoice;

import com.dduk.entity.accounting.voucher.enums.VatType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tax_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TaxInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tax_invoice_no", nullable = false, unique = true, length = 50)
    private String taxInvoiceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_invoice_type", nullable = false, length = 30)
    private TaxInvoiceType taxInvoiceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_type", nullable = false, length = 50)
    private VatType vatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaxInvoiceStatus status;

    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "supplier_business_no", nullable = false, length = 20)
    private String supplierBusinessNo;

    @Column(name = "supplier_name", nullable = false, length = 100)
    private String supplierName;

    @Column(name = "supplier_representative_name", length = 100)
    private String supplierRepresentativeName;

    @Column(name = "supplier_email", length = 100)
    private String supplierEmail;

    @Column(name = "recipient_business_no", nullable = false, length = 20)
    private String recipientBusinessNo;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_representative_name", length = 100)
    private String recipientRepresentativeName;

    @Column(name = "recipient_email", length = 100)
    private String recipientEmail;

    @Column(name = "supply_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal supplyAmount;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "external_approval_no", length = 100)
    private String externalApprovalNo;

    @Column(name = "external_status", length = 50)
    private String externalStatus;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "issue_requested_at")
    private LocalDateTime issueRequestedAt;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "amended_at")
    private LocalDateTime amendedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "taxInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<TaxInvoiceLine> lines = new ArrayList<>();

    public void addLine(TaxInvoiceLine line) {
        lines.add(line);
        line.setTaxInvoice(this);
    }

    public void requestIssue() {
        this.status = TaxInvoiceStatus.ISSUE_REQUESTED;
        this.issueRequestedAt = LocalDateTime.now();
        clearFailure();
    }

    public void markIssued(String approvalNo, String externalStatus) {
        this.status = TaxInvoiceStatus.ISSUED;
        this.externalApprovalNo = approvalNo;
        this.externalStatus = externalStatus;
        this.issuedAt = LocalDateTime.now();
        clearFailure();
    }

    public void markSent(String approvalNo, String externalStatus) {
        this.status = TaxInvoiceStatus.SENT;
        if (approvalNo != null && !approvalNo.isBlank()) {
            this.externalApprovalNo = approvalNo;
        }
        this.externalStatus = externalStatus;
        this.sentAt = LocalDateTime.now();
        clearFailure();
    }

    public void markSendFailed(String reason, String externalStatus) {
        this.status = TaxInvoiceStatus.SEND_FAILED;
        this.failureReason = reason;
        this.externalStatus = externalStatus;
    }

    public void cancel(String reason) {
        this.status = TaxInvoiceStatus.CANCELLED;
        this.failureReason = reason;
        this.cancelledAt = LocalDateTime.now();
    }

    public void markAmended(String reason) {
        this.status = TaxInvoiceStatus.AMENDED;
        this.failureReason = reason;
        this.amendedAt = LocalDateTime.now();
    }

    private void clearFailure() {
        this.failureReason = null;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = TaxInvoiceStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
