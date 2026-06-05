package com.dduk.dto.accounting.taxinvoice;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxInvoiceLineRequest {
    private String itemName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal supplyAmount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private String description;
}
