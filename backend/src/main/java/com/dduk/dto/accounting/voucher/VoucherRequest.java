package com.dduk.dto.accounting.voucher;

import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class VoucherRequest {
    private LocalDate voucherDate;
    private VoucherType voucherType;
    private VatType vatType;
    private Long vendorId;
    private String vendorNameSnapshot;
    private String description;
    private BigDecimal supplyAmount;
    private BigDecimal vatAmount;
    private BigDecimal feeAmount;
    private Long businessAccountId;
    private Long settlementAccountId;
    private List<VoucherLineRequest> lines;
}
