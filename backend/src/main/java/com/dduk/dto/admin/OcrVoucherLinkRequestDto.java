package com.dduk.dto.admin;

import com.dduk.entity.accounting.voucher.enums.VatType;
import com.dduk.entity.accounting.voucher.enums.VoucherType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class OcrVoucherLinkRequestDto {

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
}
