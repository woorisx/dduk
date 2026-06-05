package com.dduk.dto.accounting.taxinvoice;

import lombok.Data;

@Data
public class TaxInvoiceStatusUpdateRequest {
    private String approvalNo;
    private String externalStatus;
    private String reason;
}
