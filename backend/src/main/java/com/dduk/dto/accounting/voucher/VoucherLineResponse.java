package com.dduk.dto.accounting.voucher;

import com.dduk.entity.accounting.AccountSide;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VoucherLineResponse {
    private Long id;
    private Integer lineNo;
    private Long accountId;
    private String accountCode;
    private String accountName;
    private AccountSide debitCredit;
    private BigDecimal supplyAmount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String description;
    private Integer sortOrder;
}
