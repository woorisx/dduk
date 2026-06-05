package com.dduk.dto.accounting.taxinvoice;

import com.dduk.entity.accounting.taxinvoice.TaxInvoiceType;
import com.dduk.entity.accounting.voucher.enums.VatType;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TaxInvoiceCreateRequest {
    private TaxInvoiceType taxInvoiceType;
    private VatType vatType;
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
    private String memo;
    private List<TaxInvoiceLineRequest> lines;
}
