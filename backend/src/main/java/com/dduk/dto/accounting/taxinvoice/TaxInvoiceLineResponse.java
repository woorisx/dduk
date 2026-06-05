package com.dduk.dto.accounting.taxinvoice;

import com.dduk.entity.accounting.taxinvoice.TaxInvoiceLine;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxInvoiceLineResponse {
    private Long id;
    private Integer lineNo;
    private String itemName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal supplyAmount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private String description;

    public static TaxInvoiceLineResponse from(TaxInvoiceLine line) {
        TaxInvoiceLineResponse response = new TaxInvoiceLineResponse();
        response.setId(line.getId());
        response.setLineNo(line.getLineNo());
        response.setItemName(line.getItemName());
        response.setUnit(line.getUnit());
        response.setQuantity(line.getQuantity());
        response.setUnitPrice(line.getUnitPrice());
        response.setSupplyAmount(line.getSupplyAmount());
        response.setVatAmount(line.getVatAmount());
        response.setTotalAmount(line.getTotalAmount());
        response.setDescription(line.getDescription());
        return response;
    }
}
